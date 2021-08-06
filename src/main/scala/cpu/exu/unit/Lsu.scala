package cpu.exu.unit

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.{DispatchInfo, RobCommitInfo, WriteBackInfo}
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal.Const.{COMMIT_WIDTH, WRITE_BUFFER_DEPTH}
import signal._

class DCacheReadReq extends Bundle {
  val addr = UInt(32.W)
  val rob_idx = UInt(4.W)
  def init(): Unit ={
    addr := 0.U(32.W)
    rob_idx := 0.U(4.W)
  }
}

class DCacheWriteReq extends Bundle {
  val addr      = UInt(32.W)
  val data      = UInt(32.W)
  val byte_mask = UInt(4.W)
}

class DCacheResp extends Bundle {
  val data = UInt(32.W)
}

class WriteBufferInfo extends Bundle {
  val rob_idx   = UInt(4.W)
  val addr      = UInt(32.W)
  val data      = UInt(32.W)
  val byte_mask = UInt(4.W)

  def init(): Unit = {
    rob_idx   := 0.U(4.W)
    addr      := 0.U(32.W)
    data      := 0.U(32.W)
    byte_mask := 0.U(4.W)
  }
}

class LsuIO extends Bundle {
  val dispatch_info = Flipped(Decoupled(new DispatchInfo()))
  val wb_info       = Valid(new WriteBackInfo())
  val rob_commit    = Vec(COMMIT_WIDTH, Flipped(Valid(new RobCommitInfo)))
  val cache_read    = Decoupled(new DCacheReadReq)
  val cache_write   = Decoupled(new DCacheWriteReq)
  val cache_resp    = Input(new DCacheResp)
  val need_flush    = Input(Bool())
}

class Lsu extends Module {

  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }

  val io                    = IO(new LsuIO)
  val dispatch_info         = Reg(new DispatchInfo)
  val dispatch_valid        = RegInit(false.B)
  val write_buffer          = Reg(Vec(WRITE_BUFFER_DEPTH, new WriteBufferInfo))
  val write_buffer_valid    = RegInit(VecInit(Seq.fill(WRITE_BUFFER_DEPTH)(false.B)))
  val write_buffer_waiting  = RegInit(VecInit(Seq.fill(WRITE_BUFFER_DEPTH)(false.B)))
  val write_buffer_complete = RegInit(VecInit(Seq.fill(WRITE_BUFFER_DEPTH)(false.B)))
  val write_buffer_uncache  = RegInit(VecInit(Seq.fill(WRITE_BUFFER_DEPTH)(false.B)))
  val complete_head         = RegInit(1.U(WRITE_BUFFER_DEPTH.W))
  val tail                  = RegInit(1.U(WRITE_BUFFER_DEPTH.W))
  val maybe_full            = RegInit(false.B)
  val full                  = complete_head === tail && maybe_full
  val empty                 = complete_head === tail && !maybe_full
  val do_enq                = Wire(Bool())
  val do_deq                = Wire(Bool())
  val mem_addr              = (dispatch_info.imm_data.asSInt() + dispatch_info.op1_data.asSInt()).asUInt()
  val write_data            = dispatch_info.op2_data
  val is_ld                 = MuxLookup(dispatch_info.uop.asUInt(), false.B,
    Seq(
      uOP.Mm_LB.asUInt() -> true.B,
      uOP.Mm_LBU.asUInt() -> true.B,
      uOP.Mm_LH.asUInt() -> true.B,
      uOP.Mm_LHU.asUInt() -> true.B,
      uOP.Mm_LW.asUInt() -> true.B
    ))
  val is_st                 = MuxLookup(dispatch_info.uop.asUInt(), false.B,
    Seq(
      uOP.Mm_SB.asUInt() -> true.B,
      uOP.Mm_SH.asUInt() -> true.B,
      uOP.Mm_SW.asUInt() -> true.B
    ))
  val is_unsigned           = MuxLookup(dispatch_info.uop.asUInt(), false.B,
    Seq(
      uOP.Mm_LBU.asUInt() -> true.B,
      uOP.Mm_LHU.asUInt() -> true.B,
    ))
  val byte_mask             = MuxLookup(dispatch_info.uop.asUInt(), 0.U(4.W),
    Seq(
      uOP.Mm_LB.asUInt() -> "b0001".U(4.W),
      uOP.Mm_LBU.asUInt() -> "b0001".U(4.W),
      uOP.Mm_LH.asUInt() -> "b0011".U(4.W),
      uOP.Mm_LHU.asUInt() -> "b0011".U(4.W),
      uOP.Mm_LW.asUInt() -> "b1111".U(4.W),
      uOP.Mm_SB.asUInt() -> "b0001".U(4.W),
      uOP.Mm_SH.asUInt() -> "b0011".U(4.W),
      uOP.Mm_SW.asUInt() -> "b1111".U(4.W)
    ))
  val is_uncache            = mem_addr(31, 4) === "hBFD003F".U(28.W)
  io.wb_info.bits := DontCare

  //enq_logic
  do_enq := !full && is_st && dispatch_valid && !io.need_flush
  val enq_idx = OHToUInt(tail)
  when(do_enq) {
    write_buffer(enq_idx).rob_idx := dispatch_info.rob_idx
    write_buffer(enq_idx).data := write_data
    write_buffer(enq_idx).addr := mem_addr
    write_buffer(enq_idx).byte_mask := byte_mask
    write_buffer_valid(enq_idx) := true.B
    write_buffer_waiting(enq_idx) := true.B
    write_buffer_complete(enq_idx) := false.B
    write_buffer_uncache(enq_idx) := is_uncache
    tail := leftRotate(tail, 1)
  }

  //ready_logic
  val ready_head     = RegInit(1.U(WRITE_BUFFER_DEPTH.W))
  val ready_deq_idxs = (0 until COMMIT_WIDTH+1).map(i => leftRotate(ready_head, i))
  val ready_valid    = VecInit(Seq.fill(COMMIT_WIDTH)(false.B))
  val will_complete = VecInit(Seq.fill(WRITE_BUFFER_DEPTH)(false.B))

  for (i <- 0 until COMMIT_WIDTH) {
    val ready_idx = OHToUInt(ready_deq_idxs(i))
    ready_valid(i) := false.B
    for (j <- 0 until COMMIT_WIDTH) {
      when(write_buffer_waiting(ready_idx) &&write_buffer_valid(ready_idx) && io.rob_commit(j).valid && io.rob_commit(j).bits.des_rob === write_buffer(ready_idx).rob_idx) {
        write_buffer_complete(ready_idx) := true.B
        write_buffer_waiting(ready_idx) := false.B
        ready_valid(i) := true.B
        will_complete(ready_idx):=true.B
      }
    }
  }
  var next_ready_head = ready_head
  for (i <- 0 until COMMIT_WIDTH) {
    next_ready_head = Mux(ready_valid(i)&&next_ready_head =/= tail, ready_deq_idxs(i + 1), next_ready_head)
  }
  ready_head := next_ready_head


  //complete_logic
  val complete_head_idx      = OHToUInt(complete_head)
  val complete_head_info     = write_buffer(complete_head_idx)
  val complete_head_valid    = write_buffer_valid(complete_head_idx)
  val complete_head_complete = write_buffer_complete(complete_head_idx)
  do_deq := io.cache_write.ready
  io.cache_write.valid := complete_head_valid & complete_head_complete
  io.cache_write.bits.data := complete_head_info.data
  io.cache_write.bits.addr := complete_head_info.addr
  io.cache_write.bits.byte_mask := complete_head_info.byte_mask

  when(do_deq) {
    complete_head := leftRotate(complete_head, 1)
    write_buffer_valid(complete_head_idx) := false.B
  }

  when(do_enq) {
    maybe_full := true.B
  }.elsewhen(do_deq) {
    maybe_full := false.B
  }
  //load redirect and bypass logic
  val do_read         = dispatch_valid && is_ld
  val bypass_idxs     = VecInit((0 until WRITE_BUFFER_DEPTH).map(i => OHToUInt(leftRotate(tail, i))).reverse)
  val hit_buffer_mask = bypass_idxs.map(i => write_buffer(i).addr === mem_addr && (write_buffer_valid(i) || write_buffer_complete(i) && !write_buffer_uncache(i)))
  val hit_buffer_idx  = bypass_idxs(PriorityEncoder(hit_buffer_mask))
  val hit_buffer      = hit_buffer_mask.reduce(_ | _)
  val bypass_data     = write_buffer(hit_buffer_idx).data

  val op_complete    = do_enq || (do_read && (hit_buffer || io.cache_read.ready))
  val load_data      = Mux(hit_buffer, bypass_data, io.cache_resp.data)
  val load_byte_data = (0 until 4).map(i => Fill(8, mem_addr(1, 0) === i.U(2.W)) & load_data(i * 8 + 7, i * 8)).reduce(_ | _)
  val load_half_data = (0 until 2).map(i => Fill(16, mem_addr(1) === i.U(1.W)) & load_data(i * 16 + 15, i * 16)).reduce(_ | _)

  val load_final_data = MuxLookup(dispatch_info.uop.asUInt(), load_data,
    Seq(
      uOP.Mm_LB.asUInt() -> Cat(Fill(24, load_byte_data(7)), load_byte_data),
      uOP.Mm_LBU.asUInt() -> Cat(Fill(24, false.B), load_byte_data),
      uOP.Mm_LH.asUInt() -> Cat(Fill(16, load_half_data(15)), load_half_data),
      uOP.Mm_LHU.asUInt() -> Cat(Fill(16, false.B), load_half_data)
    ))

  val cache_read_bits = Reg(new DCacheReadReq)
  val cache_read_valid = RegInit(false.B)
//  cache_read_bits.addr:=mem_addr
//  cache_read_bits.rob_idx :=dispatch_info.rob_idx
//  cache_read_valid:= !hit_buffer && do_read && !op_complete
//  io.cache_read.bits:=cache_read_bits
//  io.cache_read.valid:= cache_read_valid

  io.cache_read.valid:= !hit_buffer && do_read
  io.cache_read.bits.addr:=mem_addr
  io.cache_read.bits.rob_idx :=dispatch_info.rob_idx


  io.dispatch_info.ready := !dispatch_valid || op_complete

  val wb_info       = Reg(new WriteBackInfo())
  val wb_info_valid       = RegInit(false.B)
//  wb_info.data:=load_final_data
//  wb_info.rob_idx:=dispatch_info.rob_idx
//  wb_info.is_taken:=false.B
//  wb_info.target_addr:=0.U
//  wb_info.predict_miss:=false.B
//  wb_info_valid:= op_complete && dispatch_valid
//
//  io.wb_info.bits:=wb_info
//  io.wb_info.valid:=wb_info_valid

  io.wb_info.bits.data:=load_final_data
  io.wb_info.bits.rob_idx:=dispatch_info.rob_idx
  io.wb_info.bits.is_taken:=false.B
  io.wb_info.bits.target_addr:=0.U
  io.wb_info.bits.predict_miss:=false.B
  io.wb_info.valid:= op_complete && dispatch_valid


  when(!dispatch_valid || op_complete) {
    dispatch_info := io.dispatch_info.bits
    dispatch_valid := io.dispatch_info.valid
  }
  when(io.need_flush) {
    for (i <- 0 until WRITE_BUFFER_DEPTH) {
      when(!write_buffer_complete(i) && !will_complete(i)) {
        write_buffer_valid(i) := false.B
        write_buffer_waiting(i) := false.B
      }
    }
    tail := next_ready_head
    dispatch_info.init()
    dispatch_valid :=false.B
    maybe_full := false.B
    wb_info.init()
    wb_info_valid:=false.B
    cache_read_bits.init()
    cache_read_valid:=false.B
  }

  when(reset.asBool()){
    dispatch_info.init()
    wb_info.init()
    write_buffer.foreach(i=>i.init())
    cache_read_bits.init()
  }
}
