package cpu.exu.unit

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.{DispatchInfo, WriteBackInfo}
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal._

class MduIO extends Bundle{
  val dispatch_info = Flipped(Decoupled(new DispatchInfo()))
  val wb_info       = Valid(new WriteBackInfo())
  val need_flush    = Input(Bool())
}

class Mdu extends Module{
  val io = IO(new MduIO)
  //pipe stage 1
  val dispatch_info         = Reg(new DispatchInfo)
  dispatch_info :=io.dispatch_info.bits
  val dispatch_valid        = RegInit(false.B)
  dispatch_valid:=io.dispatch_info.valid
  io.dispatch_info.ready:=true.B

  val result = dispatch_info.op1_data*dispatch_info.op2_data


  //pipe stage 2
  val wb_info       = Reg(new WriteBackInfo())
  val wb_info_valid       = RegInit(false.B)
//  wb_info.data:=result(31,0)
//  wb_info.rob_idx:=dispatch_info.rob_idx
//  wb_info.is_taken:=false.B
//  wb_info.target_addr:=0.U
//  wb_info.predict_miss:=false.B
//  wb_info_valid:=dispatch_valid
//
//  io.wb_info.bits:=wb_info
//  io.wb_info.valid:=wb_info_valid

  io.wb_info.bits.data:=result(31,0)
  io.wb_info.bits.rob_idx:=dispatch_info.rob_idx
  io.wb_info.bits.is_taken:=false.B
  io.wb_info.bits.target_addr:=0.U
  io.wb_info.bits.predict_miss:=false.B
  io.wb_info.valid:=dispatch_valid

  when(io.need_flush){
    dispatch_info.init()
    dispatch_valid:=false.B
    wb_info.init()
    wb_info_valid:=false.B
  }

  when(reset.asBool()){
    dispatch_info.init()
    wb_info.init()
  }
}
