package cpu.exu.unit

import chisel3.{util, _}
import chisel3.util._
import chisel3.experimental._
import cpu.exu.{DispatchInfo, WriteBackInfo}
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal._

class HpuIO extends Bundle {
  val dispatch_info = Flipped(Decoupled(new DispatchInfo()))
  val wb_info       = Valid(new WriteBackInfo())
  val need_flush    = Input(Bool())
}

class Hpu extends Module{
  val io = IO(new HpuIO)
  val dispatch_info         = Wire(new DispatchInfo)
  dispatch_info :=io.dispatch_info.bits
  val dispatch_valid        = WireInit(false.B)
  dispatch_valid:=io.dispatch_info.valid
  io.dispatch_info.ready:=true.B


  val op1_data    = dispatch_info.op1_data
  val op2_data    = Mux(dispatch_info.need_imm, dispatch_info.imm_data,dispatch_info.op2_data)
  val result_data = Wire(UInt(32.W))

  result_data := 0.U
  switch(dispatch_info.uop) {
    is(uOP.Hpu_0) {
      result_data := PopCount(op1_data)
    }
  }
  val wb_info       = Reg(new WriteBackInfo())
  val wb_info_valid       = RegInit(false.B)
    wb_info.data:=result_data
    wb_info.rob_idx:=dispatch_info.rob_idx
    wb_info.is_taken:=false.B
    wb_info.target_addr:=0.U
    wb_info.predict_miss:=false.B
    wb_info_valid:=dispatch_valid

    io.wb_info.bits:=wb_info
    io.wb_info.valid:=wb_info_valid



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
