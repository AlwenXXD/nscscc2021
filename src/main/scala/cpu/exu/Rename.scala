package cpu.exu

import chisel3.{Vec, _}
import chisel3.util._
import chisel3.experimental._
import instructions.MIPS32._
import signal.Const.{COMMIT_WIDTH, ISSUE_WIDTH, ROB_IDX_WIDTH}
import signal._

class RobCommitInfo extends Bundle {
  val des_rob = UInt(ROB_IDX_WIDTH.W)
  val commit_addr = UInt(5.W)
  val commit_data = UInt(32.W)
  def init(): Unit ={
    des_rob := 0.U(ROB_IDX_WIDTH.W)
    commit_addr := 0.U(5.W)
    commit_data := 0.U(32.W)
  }
}

class RobInitInfo extends Bundle {
  val is_valid = Bool()
  val des_rob = UInt(ROB_IDX_WIDTH.W)
  val op1_rob = UInt(ROB_IDX_WIDTH.W)
  val op2_rob = UInt(ROB_IDX_WIDTH.W)
  val op1_regData = UInt(32.W)
  val op2_regData = UInt(32.W)
  val op1_in_rob = Bool()
  val op2_in_rob = Bool()
  def init(): Unit ={
    is_valid := false.B
    des_rob := 0.U(ROB_IDX_WIDTH.W)
    op1_rob := 0.U(ROB_IDX_WIDTH.W)
    op2_rob := 0.U(ROB_IDX_WIDTH.W)
    op1_regData := 0.U(32.W)
    op2_regData := 0.U(32.W)
    op1_in_rob := false.B
    op2_in_rob := false.B
  }
}

class RegReadIO extends Bundle {
  val op1_addr = Output(UInt(5.W))
  val op2_addr = Output(UInt(5.W))
  val op1_data = Input(UInt(32.W))
  val op2_data = Input(UInt(32.W))
}

class RobReadIO extends Bundle {
  val op1_idx = Output(UInt(ROB_IDX_WIDTH.W))
  val op2_idx = Output(UInt(ROB_IDX_WIDTH.W))
  val op1_data = Input(UInt(32.W))
  val op2_data = Input(UInt(32.W))
  val op1_ready = Input(Bool())
  val op2_ready = Input(Bool())
}


class RenameIO extends Bundle {
  val decode_info = Flipped(Valid(Vec(ISSUE_WIDTH, new DecodeInfo)))
  val rob_commit  = Vec(COMMIT_WIDTH, Flipped(Valid(new RobCommitInfo)))
  val rename_info = Valid(Vec(ISSUE_WIDTH, new RenameInfo))
  val reg_read = Vec(ISSUE_WIDTH, new RegReadIO())
  val need_flush = Input(Bool())
  val need_stop= Input(Bool())
}

class Rename extends Module {
  val io = IO(new RenameIO)
  val busy_table = RegInit(0.U(32.W))
  val map_table = RegInit(VecInit(Seq.fill(32)(0.U(ROB_IDX_WIDTH.W))))

  val will_busy = io.decode_info.bits.map(i=>UIntToOH(i.des_addr)&Fill(32,io.decode_info.valid&&i.is_valid)).reduce(_|_)& Cat(Fill(31,true.B),false.B)
  val busy_table_wb = busy_table & io.rob_commit.map(i=>UIntToOH(i.bits.commit_addr)&Fill(32,i.valid&&i.bits.des_rob===map_table(i.bits.commit_addr))).reduce(_|_).do_unary_~  & Cat(Fill(31,true.B),false.B)
  val busy_table_next = will_busy | busy_table_wb
  busy_table:=Mux(io.need_flush,0.U(32.W),busy_table_next)

  val remap_idx = io.decode_info.bits.map(i=>UIntToOH(i.des_addr))
  for (i <- 1 until 32) {
    map_table(i) := io.decode_info.bits.zipWithIndex.foldLeft(map_table(i)){case (p,(info,k)) => Mux(io.decode_info.valid&&info.is_valid && remap_idx(k)(i), info.des_rob, p)}
  }

  val rename_info = Reg(Vec(ISSUE_WIDTH, new RenameInfo))
  val rename_info_valid = RegInit(false.B)
  io.rename_info.bits := rename_info
  io.rename_info.valid := rename_info_valid

  for (i <- 0 until ISSUE_WIDTH) {
    io.reg_read(i).op1_addr := io.decode_info.bits(i).op1_addr
    io.reg_read(i).op2_addr := io.decode_info.bits(i).op2_addr

    rename_info(i).op1_rob := (0 until i).foldLeft(map_table(io.decode_info.bits(i).op1_addr)) {
      (p, k) => Mux(io.decode_info.bits(k).is_valid && io.decode_info.bits(k).des_addr === io.decode_info.bits(i).op1_addr, io.decode_info.bits(k).des_rob, p)
    }
    rename_info(i).op2_rob := (0 until i).foldLeft(map_table(io.decode_info.bits(i).op2_addr)) {
      (p, k) => Mux(io.decode_info.bits(k).is_valid && io.decode_info.bits(k).des_addr === io.decode_info.bits(i).op2_addr, io.decode_info.bits(k).des_rob, p)
    }
    rename_info(i).op1_in_rob:=(0 until i).map(k=>io.decode_info.bits(k).is_valid && io.decode_info.bits(k).des_addr === io.decode_info.bits(i).op1_addr&& io.decode_info.bits(k).des_addr =/= 0.U).foldLeft(busy_table_wb(io.decode_info.bits(i).op1_addr))(_|_)
    rename_info(i).op2_in_rob:=(0 until i).map(k=>io.decode_info.bits(k).is_valid && io.decode_info.bits(k).des_addr === io.decode_info.bits(i).op2_addr&& io.decode_info.bits(k).des_addr =/= 0.U).foldLeft(busy_table_wb(io.decode_info.bits(i).op2_addr))(_|_)
    rename_info(i).op1_regData := io.reg_read(i).op1_data
    rename_info(i).op2_regData := io.reg_read(i).op2_data
    rename_info(i).op1_robData := 0.U
    rename_info(i).op2_robData := 0.U
    rename_info(i).op1_rob_ready := false.B
    rename_info(i).op2_rob_ready := false.B
    rename_info(i).des_rob:=io.decode_info.bits(i).des_rob
    rename_info(i).is_valid:=io.decode_info.bits(i).is_valid
    rename_info(i).inst_addr    :=io.decode_info.bits(i).inst_addr
    rename_info(i).uop          :=io.decode_info.bits(i).uop
    rename_info(i).unit_sel     :=io.decode_info.bits(i).unit_sel
    rename_info(i).need_imm     :=io.decode_info.bits(i).need_imm
    rename_info(i).imm_data     :=io.decode_info.bits(i).imm_data
    rename_info(i).predict_taken:=io.decode_info.bits(i).predict_taken
  }
  rename_info_valid:=io.decode_info.valid

  when(io.need_stop){
    busy_table := busy_table
    map_table := map_table
    rename_info := rename_info
    rename_info_valid := rename_info_valid
  }

  when(io.need_flush){
    rename_info.foreach(_.init())
    rename_info_valid:=false.B
  }

  when(reset.asBool()){
    rename_info.foreach(_.init())
  }


}
