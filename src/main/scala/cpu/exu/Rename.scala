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


class RenameIO extends Bundle {
  val rename_info = Flipped(Valid(Vec(ISSUE_WIDTH, new DecodeInfo)))
  val rob_commit  = Vec(COMMIT_WIDTH, Flipped(Valid(new RobCommitInfo)))
  val reg_read = Vec(ISSUE_WIDTH, new RegReadIO())
  val rob_init_info = Valid(Vec(ISSUE_WIDTH, new RobInitInfo))
  val need_flush = Input(Bool())
}

class Rename extends Module {
  val io = IO(new RenameIO)
  val busy_table = RegInit(0.U(32.W))
  val map_table = RegInit(VecInit(Seq.fill(32)(0.U(ROB_IDX_WIDTH.W))))

  val will_busy = io.rename_info.bits.map(i=>UIntToOH(i.des_addr)&Fill(32,io.rename_info.valid&&i.is_valid)).reduce(_|_)& Cat(Fill(31,true.B),false.B)
  val busy_table_wb = busy_table & io.rob_commit.map(i=>UIntToOH(i.bits.commit_addr)&Fill(32,i.valid&&i.bits.des_rob===map_table(i.bits.commit_addr))).reduce(_|_).do_unary_~  & Cat(Fill(31,true.B),false.B)
  val busy_table_next = will_busy | busy_table_wb
  busy_table:=Mux(io.need_flush,0.U(32.W),busy_table_next)

  val remap_idx = io.rename_info.bits.map(i=>UIntToOH(i.des_addr))
  for (i <- 1 until 32) {
    map_table(i) := io.rename_info.bits.zipWithIndex.foldLeft(map_table(i)){case (p,(info,k)) => Mux(io.rename_info.valid&&info.is_valid && remap_idx(k)(i), info.des_rob, p)}
  }

  val rob_init_info = Reg(Vec(ISSUE_WIDTH, new RobInitInfo))
  val rob_init_info_valid = RegInit(false.B)
  io.rob_init_info.bits := rob_init_info
  io.rob_init_info.valid := rob_init_info_valid

  for (i <- 0 until ISSUE_WIDTH) {
    rob_init_info(i).op1_rob := (0 until i).foldLeft(map_table(io.rename_info.bits(i).op1_addr)) {
      (p, k) => Mux(io.rename_info.bits(k).is_valid && io.rename_info.bits(k).des_addr === io.rename_info.bits(i).op1_addr, io.rename_info.bits(k).des_rob, p)
    }
    rob_init_info(i).op2_rob := (0 until i).foldLeft(map_table(io.rename_info.bits(i).op2_addr)) {
      (p, k) => Mux(io.rename_info.bits(k).is_valid && io.rename_info.bits(k).des_addr === io.rename_info.bits(i).op2_addr, io.rename_info.bits(k).des_rob, p)
    }
    rob_init_info(i).op1_in_rob:=(0 until i).map(k=>io.rename_info.bits(k).is_valid && io.rename_info.bits(k).des_addr === io.rename_info.bits(i).op1_addr&& io.rename_info.bits(k).des_addr =/= 0.U).foldLeft(busy_table_wb(io.rename_info.bits(i).op1_addr))(_|_)
    rob_init_info(i).op2_in_rob:=(0 until i).map(k=>io.rename_info.bits(k).is_valid && io.rename_info.bits(k).des_addr === io.rename_info.bits(i).op2_addr&& io.rename_info.bits(k).des_addr =/= 0.U).foldLeft(busy_table_wb(io.rename_info.bits(i).op2_addr))(_|_)
    io.reg_read(i).op1_addr := io.rename_info.bits(i).op1_addr
    io.reg_read(i).op2_addr := io.rename_info.bits(i).op2_addr
    rob_init_info(i).op1_regData := io.reg_read(i).op1_data
    rob_init_info(i).op2_regData := io.reg_read(i).op2_data
    rob_init_info(i).des_rob:=io.rename_info.bits(i).des_rob
    rob_init_info(i).is_valid:=io.rename_info.bits(i).is_valid
  }
  rob_init_info_valid:=io.rename_info.valid

  when(io.need_flush){
    rob_init_info.foreach(_.init())
    rob_init_info_valid:=false.B
  }

  when(reset.asBool()){
    rob_init_info.foreach(_.init())
  }


}
