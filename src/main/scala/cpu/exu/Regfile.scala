package cpu.exu

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal.Const.{COMMIT_WIDTH, ISSUE_WIDTH}
import signal._

class RegfileIO extends Bundle{
  val reg_read = Vec(ISSUE_WIDTH, Flipped(new RegReadIO()))
  val rob_commit_i = Vec(COMMIT_WIDTH,Flipped(Valid(new RobCommitInfo())))
}

class Regfile extends Module{
  val io =IO(new RegfileIO)
  val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  val next_data = Wire(Vec(32, UInt(32.W)))
  val commit_idx = io.rob_commit_i.map(i=>UIntToOH(i.bits.commit_addr))

  next_data(0):=0.U(32.W)
  for(i<-1 until 32){
    next_data(i) := io.rob_commit_i.zipWithIndex.foldLeft(regfile(i)){case (p,(info,k))=>Mux(info.valid&&commit_idx(k)(i),info.bits.commit_data,p)}
  }
  regfile:=next_data
  for(i<-0 until ISSUE_WIDTH){
    io.reg_read(i).op1_data:=next_data(io.reg_read(i).op1_addr)
    io.reg_read(i).op2_data:=next_data(io.reg_read(i).op2_addr)
  }



}
