//package io
//
//import chisel3._
//import chisel3.util._
//import chisel3.experimental._
//import cpu.exu.unit.{Alu, Bju}
//import cpu.ifu.{BranchInfo, FBInstBank}
//import instructions.MIPS32._
//import signal._
//
//
//
//class SramReadReq extends Bundle{
//  val addr = UInt(32.W)
//}
//class SramReadResp extends Bundle{
//  val data = UInt(32.W)
//}
//
//class SramReadIO extends Bundle {
//  val resp = Valid(new SramReadResp)
//  val req     =  Flipped(Decoupled(new SramReadReq))
//  def idle(): Unit ={
//    req.ready:=false.B
//    resp.bits.data:=0.U(32.W)
//    resp.valid:=false.B
//  }
//  def busy(): Unit ={
//    resp.valid:=false.B
//    req.ready:=false.B
//    resp.bits.data:=0.U(32.W)
//  }
//  def ready(rData:UInt): Unit ={
//    resp.bits.data:=rData
//    resp.valid:=true.B
//    req.ready:=true.B
//  }
//}
//
//class SramWriteReq extends Bundle{
//  val addr = UInt(32.W)
//  val data = UInt(32.W)
//  val be_n = UInt(4.W)
//}
//
//class SramWriteIO extends Bundle {
//  val req     =  Flipped(Decoupled(new SramWriteReq))
//  def idle(): Unit ={
//    req.ready:=false.B
//  }
//  def busy(): Unit ={
//    req.ready:=false.B
//  }
//  def ready(): Unit ={
//    req.ready:=true.B
//  }
//}
//
//class SramIO extends Bundle {
//  val ram_rd = new SramReadIO
//  val ram_wr = new SramWriteIO
//  val ram_ctrl = new SramCtrlIO
//}
//
//class Sram extends Module {
//  val io = IO(new SramIO)
//
//  when(io.ram_rd.req.valid){
//    io.ram_ctrl.read(io.ram_rd.req.bits.addr)
//    io.ram_rd.ready(io.ram_ctrl.data_in)
//    io.ram_wr.busy()
//  }.elsewhen(io.ram_wr.req.valid){
//    io.ram_ctrl.write(io.ram_wr.req.bits.addr,io.ram_wr.req.bits.data,io.ram_wr.req.bits.be_n)
//    io.ram_wr.ready()
//    io.ram_rd.busy()
//  }.otherwise{
//    io.ram_ctrl.idle()
//    io.ram_rd.idle()
//    io.ram_wr.idle()
//  }
//
//}
