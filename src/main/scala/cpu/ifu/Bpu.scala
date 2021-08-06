package cpu.ifu

import chisel3.{Bool, _}
import chisel3.util._
import instructions.MIPS32._
import signal.Const._

class BpuInstPacket extends Bundle {
  val data         = Vec(FETCH_WIDTH, UInt(32.W))
  val addr         = UInt(32.W)
  val gh_backup    = UInt(4.W)
  val valid_mask   = Vec(FETCH_WIDTH, Bool())
  val delay_mask   = Vec(FETCH_WIDTH, Bool())
  val branch_mask  = Vec(FETCH_WIDTH, Bool())
  val predict_mask = Vec(FETCH_WIDTH, Bool())
  def init(): Unit ={
    data         := VecInit(Seq.fill(FETCH_WIDTH)(0.U(32.W)))
    addr         := 0.U(32.W)
    gh_backup    := 0.U(4.W)
    valid_mask   := VecInit(Seq.fill(FETCH_WIDTH)(false.B))
    delay_mask   := VecInit(Seq.fill(FETCH_WIDTH)(false.B))
    branch_mask  := VecInit(Seq.fill(FETCH_WIDTH)(false.B))
    predict_mask  := VecInit(Seq.fill(FETCH_WIDTH)(false.B))
  }
}


class BranchPredictionInfo extends Bundle {
  val predict_addr = UInt(32.W)
  val is_taken     = Bool()
  val take_delay   = Bool()
}

class BranchInfo extends Bundle {
  val target_addr  = UInt(32.W)
  val inst_addr    = UInt(32.W)
  val gh_update    = UInt(4.W)
  val is_branch    = Bool()
  val is_taken     = Bool()
  val predict_miss = Bool()
  def init(): Unit ={
    target_addr  := 0.U(32.W)
    inst_addr    := 0.U(32.W)
    gh_update    := 0.U(4.W)
    is_branch    := false.B
    is_taken     := false.B
    predict_miss := false.B
  }
}

class BpuDebugIO extends Bundle {
  val branch_mask    = Output(UInt(FETCH_WIDTH.W))
  val fetched_mask   = Output(UInt(FETCH_WIDTH.W))
  val predict_branch = Output(UInt(FETCH_WIDTH.W))
  val predict_addr   = Output(UInt(32.W))
  val is_taken       = Output(Bool())
  val take_delay     = Output(Bool())
  val inst_packet = Output(Vec(FETCH_WIDTH, UInt(32.W)))
}

class BpuIO extends Bundle {
  val inst_packet_i     = Flipped(Valid(new InstPacket))
  val resp_o            = Valid(new BranchPredictionInfo())
  val bpu_inst_packet_o = Decoupled(new BpuInstPacket)
  val branch_info_i     = Flipped(Valid(new BranchInfo))
  val is_delay          = Input(Bool())
  val need_flush    = Input(Bool())
  val bpu_debug = new BpuDebugIO
}

class BPU extends Module {
  val io             = IO(new BpuIO())
  val global_history = RegInit(0.U(4.W))
  val predictor      = RegInit(VecInit(Seq.fill(128)("b10".U(2.W))))

  val branch_mask = io.inst_packet_i.bits.data.map(inst => {
    Lookup(inst, false.B, Array(
      BEQ -> true.B,
      BNE -> true.B,
      BLEZ -> true.B,
      BGTZ -> true.B,
      BLTZ -> true.B,
      BGEZ -> true.B,
      BGEZAL -> true.B,
      BLTZAL -> true.B,
    ))
  })

  val fetched_mask = Mux(io.is_delay, 1.U(FETCH_WIDTH.W), VecInit(UIntToOH(io.inst_packet_i.bits.addr(ICACHE_INST_WIDTH+2-1, 2)).asBools().scanLeft(false.B)(_ | _).drop(1)).asUInt()).asBools()

  val need_predict = (branch_mask zip fetched_mask).map(i => i._1 & i._2)

  val predictor_idx = VecInit(need_predict.zipWithIndex.map {
    case (_, i) => Cat(global_history, io.inst_packet_i.bits.addr(5), i.U(ICACHE_INST_WIDTH.W))
  })
  val predict_mask  = predictor_idx.map(idx => predictor(idx)(1) === true.B)

  val predict_branch = need_predict.zip(predict_mask).map(i => i._1 & i._2)

  val is_taken = VecInit(predict_branch).asUInt().orR()

  val inst_mask = PriorityEncoderOH(predict_branch)

  val inst_idx = OHToUInt(inst_mask)

  val inst = io.inst_packet_i.bits.data(inst_idx)

  val b_imm = Cat(Fill(14, inst(15)), inst(15, 0), 0.U(2.W))

  val predict_addr = (b_imm.asSInt() + Cat(io.inst_packet_i.bits.addr(31, ICACHE_INST_WIDTH+2), inst_idx, 0.U(2.W)).asSInt() + 4.S(32.W)).asUInt()


  //val delay_mask = branch_mask

  val delay_inst_idx = inst_idx + 1.U

  val take_delay = (inst_idx === (FETCH_WIDTH-1).U) & is_taken

  val valid_mask = VecInit(inst_mask.scanRight((~is_taken).asBool())(_ | _).dropRight(1).zip(fetched_mask).map(i => i._1 & i._2))

  when(!take_delay && is_taken) {
    valid_mask(delay_inst_idx) := true.B
  }

  global_history := Mux(io.branch_info_i.valid && io.branch_info_i.bits.predict_miss, io.branch_info_i.bits.gh_update, Cat(is_taken, global_history(3, 1)))
  val target_predictor = predictor(Cat(io.branch_info_i.bits.gh_update, io.branch_info_i.bits.inst_addr(5, 2)))
  when(io.branch_info_i.valid && io.branch_info_i.bits.is_branch) {
    switch(target_predictor) {
      is("b00".U(2.W)) {
        when(io.branch_info_i.bits.is_taken) {
          target_predictor := "b01".U(2.W)
        }.otherwise {
          target_predictor := "b00".U(2.W)
        }
      }
      is("b01".U(2.W)) {
        when(io.branch_info_i.bits.is_taken) {
          target_predictor := "b11".U(2.W)
        }.otherwise {
          target_predictor := "b00".U(2.W)
        }
      }
      is("b10".U(2.W)) {
        when(io.branch_info_i.bits.is_taken) {
          target_predictor := "b11".U(2.W)
        }.otherwise {
          target_predictor := "b00".U(2.W)
        }
      }
      is("b11".U(2.W)) {
        when(io.branch_info_i.bits.is_taken) {
          target_predictor := "b11".U(2.W)
        }.otherwise {
          target_predictor := "b10".U(2.W)
        }
      }
    }
  }

  io.resp_o.bits.predict_addr := predict_addr
  io.resp_o.bits.take_delay := take_delay
  io.resp_o.bits.is_taken := is_taken
  io.resp_o.valid := io.inst_packet_i.valid

  val bpu_inst_packet_o = Reg(new BpuInstPacket)
  val bpu_inst_packet_o_valid = RegInit(false.B)

  when(io.bpu_inst_packet_o.ready){
    bpu_inst_packet_o_valid := io.inst_packet_i.valid
    bpu_inst_packet_o.valid_mask := valid_mask
    bpu_inst_packet_o.addr := io.inst_packet_i.bits.addr
    bpu_inst_packet_o.data := io.inst_packet_i.bits.data
    bpu_inst_packet_o.delay_mask := branch_mask
    bpu_inst_packet_o.predict_mask := predict_branch
    bpu_inst_packet_o.branch_mask := branch_mask
    bpu_inst_packet_o.gh_backup := global_history
  }
  io.bpu_inst_packet_o.bits:=bpu_inst_packet_o
  io.bpu_inst_packet_o.valid:=bpu_inst_packet_o_valid


//  io.bpu_inst_packet_o.valid := io.inst_packet_i.valid
//  io.bpu_inst_packet_o.bits.valid_mask := valid_mask
//  io.bpu_inst_packet_o.bits.addr := io.inst_packet_i.bits.addr
//  io.bpu_inst_packet_o.bits.data := io.inst_packet_i.bits.data
//  io.bpu_inst_packet_o.bits.delay_mask := branch_mask
//  io.bpu_inst_packet_o.bits.predict_mask := predict_branch
//  io.bpu_inst_packet_o.bits.branch_mask := branch_mask
//  io.bpu_inst_packet_o.bits.gh_backup := global_history

  when(io.need_flush){
    bpu_inst_packet_o.init()
    bpu_inst_packet_o_valid:=false.B
  }

  when(reset.asBool()){
    bpu_inst_packet_o.init()
  }

  //debug
  io.bpu_debug.branch_mask:=VecInit(branch_mask).asUInt()
  io.bpu_debug.fetched_mask:=VecInit(fetched_mask).asUInt()
  io.bpu_debug.predict_branch:=VecInit(predict_branch).asUInt()
  io.bpu_debug.predict_addr:=predict_addr
  io.bpu_debug.is_taken:=is_taken
  io.bpu_debug.take_delay:=take_delay
  io.bpu_debug.inst_packet:=io.inst_packet_i.bits.data

}
