package cpu.exu

import chisel3.{UInt, _}
import chisel3.util._
import chisel3.experimental._
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal.Const.{ISSUE_WIDTH, ROB_DEPTH, ROB_IDX_WIDTH}
import signal._


class Decoder extends Bundle {
  val uop             = uOP()
  val unit_sel        = UnitSel()
  val except_type     = ExceptSel()
  val target_sel      = TargetSel()
  val flush_on_commit = Bool()
  val need_imm        = Bool()
  val op1_addr        = UInt(5.W)
  val op2_addr        = UInt(5.W)
  val des_addr        = UInt(5.W)
  val imm_data        = UInt(32.W)

  def decode(inst: UInt) = {
    val imm_sel = Wire(ImmSel())
    val op1_sel = Wire(OpSel())
    val op2_sel = Wire(OpSel())
    val des_sel = Wire(OpSel())

    val decoder = ListLookup(inst, decode_default, decode_table)
    val signals = Seq(uop, unit_sel, imm_sel, op1_sel, op2_sel, des_sel, flush_on_commit, target_sel)
    signals zip decoder foreach { case (s, d) => s := d }

    val rs          = inst(25, 21)
    val rt          = inst(20, 16)
    val rd          = inst(15, 11)
    val immediate   = inst(15, 0)
    val instr_index = inst(25, 0)
    val sa          = inst(10, 6)

    except_type := MuxLookup(uop.asUInt(), ExceptSel.is_null.asUInt(), Array(
      uOP.SYSCALL.asUInt() -> ExceptSel.is_syscall.asUInt(),
      uOP.BREAK.asUInt() -> ExceptSel.is_break.asUInt(),
    )).asTypeOf(ExceptSel())

    need_imm := imm_sel =/= ImmSel.no_imm
    op1_addr := MuxLookup(op1_sel.asUInt(), 0.U(5.W), Array(
      OpSel.is_rs.asUInt() -> rs,
      OpSel.is_rt.asUInt() -> rt,
      OpSel.is_rd.asUInt() -> rd,
      OpSel.is_ra.asUInt() -> 31.U(5.W)
    ).toSeq)
    op2_addr := MuxLookup(op2_sel.asUInt(), 0.U(5.W), Array(
      OpSel.is_rs.asUInt() -> rs,
      OpSel.is_rt.asUInt() -> rt,
      OpSel.is_rd.asUInt() -> rd,
      OpSel.is_ra.asUInt() -> 31.U(5.W)
    ).toSeq)
    des_addr := MuxLookup(des_sel.asUInt(), 0.U(5.W), Array(
      OpSel.is_rs.asUInt() -> rs,
      OpSel.is_rt.asUInt() -> rt,
      OpSel.is_rd.asUInt() -> rd,
      OpSel.is_ra.asUInt() -> 31.U(5.W)
    ).toSeq)
    imm_data := MuxLookup(imm_sel.asUInt(), 0.U(32.W), Array(
      ImmSel.sign_ex.asUInt() -> Cat(Fill(16, immediate(15)), immediate),
      ImmSel.zero_ex.asUInt() -> Cat(Fill(16, 0.U(1.W)), immediate),
      ImmSel.is_sa.asUInt() -> Cat(Fill(27, 0.U(1.W)), sa),
      ImmSel.is_instr_index.asUInt() -> Cat(Fill(10, 0.U(1.W)), instr_index),
    ).toSeq)
    this
  }
}

class DecodeInfo extends Bundle {
  val is_valid      = Bool()
  val inst_addr = UInt(32.W)
  val op1_addr      = UInt(5.W)
  val op2_addr      = UInt(5.W)
  val des_addr      = UInt(5.W)
  val des_rob       = UInt(ROB_IDX_WIDTH.W)
  val predict_taken = Bool()
  val imm_data      = UInt(32.W)
  val uop           = uOP()
  val unit_sel      = UnitSel()
  val need_imm      = Bool()
  def init(): Unit ={
    is_valid := false.B
    inst_addr:= 0.U(32.W)
    op1_addr := 0.U(5.W)
    op2_addr := 0.U(5.W)
    des_addr := 0.U(5.W)
    des_rob  := 0.U(ROB_IDX_WIDTH.W)
    predict_taken := false.B
    imm_data      := 0.U(32.W)
    uop           := uOP.NOP
    unit_sel      := UnitSel.is_Null
    need_imm      := false.B
  }
}
class FBRespInfo extends Bundle{
  val deq_valid = Vec(ISSUE_WIDTH,Bool())
}

class DecodeIO extends Bundle {
  val fb_inst_bank = Flipped(Valid(new FBInstBank))
  val fb_resp = Output(new FBRespInfo())
  val rob_allocate = Flipped(new RobAllocateIO)
  val decode_info  = Valid(Vec(ISSUE_WIDTH, new DecodeInfo))
  val need_flush   = Input(Bool())
  val need_stop   = Input(Bool())
}


class Decode extends Module {
  val io = IO(new DecodeIO)

  val decode_info = Wire(Vec(ISSUE_WIDTH, new Decoder))
  decode_info.zip(io.fb_inst_bank.bits.data).foreach(i => i._1.decode(i._2.inst))

  //pipe stage
  val decode_info_bits  = Reg(Vec(ISSUE_WIDTH, new DecodeInfo))
  val decode_info_valid = RegInit(false.B)

  io.rob_allocate.allocate_req.bits:= VecInit(io.fb_inst_bank.bits.data.map(_.is_valid))
  io.rob_allocate.allocate_req.valid:=io.fb_inst_bank.valid

  val rob_allocate_info_bits = Reg(Vec(ISSUE_WIDTH,new RobAllocateInfo))
  val rob_allocate_info_valid = RegInit(false.B)
  rob_allocate_info_valid := io.fb_inst_bank.valid

  io.rob_allocate.allocate_info.bits:=rob_allocate_info_bits
  io.rob_allocate.allocate_info.valid:=rob_allocate_info_valid

  for (i <- 0 until ISSUE_WIDTH) {
    rob_allocate_info_bits(i).rob_idx := io.rob_allocate.allocate_resp.bits.rob_idx(i)
    rob_allocate_info_bits(i).inst_valid := io.rob_allocate.allocate_resp.bits.enq_valid_mask(i)
    rob_allocate_info_bits(i).inst_addr := io.fb_inst_bank.bits.data(i).inst_addr
    rob_allocate_info_bits(i).commit_addr := decode_info(i).des_addr
    rob_allocate_info_bits(i).commit_target := decode_info(i).target_sel
    rob_allocate_info_bits(i).except_type := decode_info(i).except_type
    rob_allocate_info_bits(i).is_branch := io.fb_inst_bank.bits.data(i).is_branch
    rob_allocate_info_bits(i).is_delay := io.fb_inst_bank.bits.data(i).is_delay
    rob_allocate_info_bits(i).flush_on_commit := decode_info(i).flush_on_commit
    rob_allocate_info_bits(i).gh_info := io.fb_inst_bank.bits.data(i).gh_backup


    decode_info_bits(i).inst_addr := io.fb_inst_bank.bits.data(i).inst_addr
    decode_info_bits(i).predict_taken := io.fb_inst_bank.bits.data(i).predict_taken
    decode_info_bits(i).imm_data      := decode_info(i).imm_data
    decode_info_bits(i).uop           := decode_info(i).uop
    decode_info_bits(i).unit_sel      := decode_info(i).unit_sel
    decode_info_bits(i).need_imm      := decode_info(i).need_imm
    decode_info_bits(i).is_valid := io.rob_allocate.allocate_resp.bits.enq_valid_mask(i)
    decode_info_bits(i).op1_addr := decode_info(i).op1_addr
    decode_info_bits(i).op2_addr := decode_info(i).op2_addr
    decode_info_bits(i).des_addr := decode_info(i).des_addr
    decode_info_bits(i).des_rob  := io.rob_allocate.allocate_resp.bits.rob_idx(i)
  }
  decode_info_valid := io.fb_inst_bank.valid && io.rob_allocate.allocate_resp.valid

  io.decode_info.bits:=decode_info_bits
  io.decode_info.valid:=decode_info_valid

  io.fb_resp.deq_valid:=io.rob_allocate.allocate_resp.bits.enq_valid_mask

  when(io.need_stop){
    decode_info_bits := decode_info_bits
    decode_info_valid:= decode_info_valid
    rob_allocate_info_bits:=rob_allocate_info_bits
    rob_allocate_info_valid:=rob_allocate_info_valid
  }

  when(io.need_flush){
    decode_info_bits.foreach(_.init())
    decode_info_valid:=false.B
    rob_allocate_info_bits.foreach(_.init())
    rob_allocate_info_valid:=false.B
  }

  when(reset.asBool()){
    decode_info_bits.foreach(_.init())
    rob_allocate_info_bits.foreach(_.init())
  }

}
