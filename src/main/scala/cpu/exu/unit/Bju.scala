package cpu.exu.unit

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.{DispatchInfo, WriteBackInfo}
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal._

class BjuIO extends Bundle{
  val dispatch_info = Flipped(Decoupled(new DispatchInfo()))
  val wb_info       = Valid(new WriteBackInfo())
  val need_flush    = Input(Bool())
}


class Bju extends Module{
  val io = IO(new BjuIO)

  val dispatch_info         = Reg(new DispatchInfo)
  dispatch_info :=io.dispatch_info.bits
  val dispatch_valid        = RegInit(false.B)
  dispatch_valid:=io.dispatch_info.valid
  io.dispatch_info.ready:=true.B

  val target_addr = Wire(UInt(32.W))
  val next_addr = dispatch_info.inst_addr+8.U(32.W)
  val branch_addr = (dispatch_info.inst_addr.asSInt()+Cat(dispatch_info.imm_data(29,0), 0.U(2.W)).asSInt()+4.S(32.W)).asUInt()
  val jump_addr = Cat(dispatch_info.inst_addr(31,28),dispatch_info.imm_data(25,0),0.U(2.W))
  val is_taken = Wire(Bool())

  val eq=dispatch_info.op1_data===dispatch_info.op2_data
  val ez = dispatch_info.op1_data===0.U(32.W)
  val ltz=dispatch_info.op1_data.asSInt()<0.S(32.W)

  io.wb_info.bits:=DontCare

  is_taken:=false.B
  target_addr:=0.U(32.W)
  switch(dispatch_info.uop){
    is(uOP.Bju_J ){
      is_taken:=true.B
      target_addr:=jump_addr
    }
    is(uOP.Bju_JAL ){
      is_taken:=true.B
      target_addr:=jump_addr
    }
    is(uOP.Bju_JR ){
      is_taken:=true.B
      target_addr:=dispatch_info.op1_data
    }
    is(uOP.Bju_JALR ){
      is_taken:=true.B
      target_addr:=dispatch_info.op1_data
    }
    is(uOP.Bju_BEQ ){
      is_taken:=eq
      target_addr:=branch_addr
    }
    is(uOP.Bju_BNE ){
      is_taken:= !eq
      target_addr:=branch_addr
    }
    is(uOP.Bju_BLEZ ){
      is_taken:= ltz||ez
      target_addr:=branch_addr
    }
    is(uOP.Bju_BGTZ ){
      is_taken:= !ltz && !ez
      target_addr:=branch_addr
    }
    is(uOP.Bju_BLTZ ){
      is_taken:= ltz
      target_addr:=branch_addr
    }
    is(uOP.Bju_BGEZ ){
      is_taken:= !ltz
      target_addr:=branch_addr
    }
    is(uOP.Bju_BGEZAL ){
      is_taken:= !ltz && !ez
      target_addr:=branch_addr
    }
    is(uOP.Bju_BLTZAL){
      is_taken:= ltz
      target_addr:=branch_addr
    }

  }


  io.wb_info.valid:= dispatch_valid
  io.wb_info.bits.is_taken:=is_taken
  io.wb_info.bits.target_addr:=Mux( !is_taken&&dispatch_info.predict_taken,next_addr,target_addr)
  io.wb_info.bits.rob_idx:=dispatch_info.rob_idx
  io.wb_info.bits.data:=next_addr
  io.wb_info.bits.predict_miss:=is_taken^dispatch_info.predict_taken

  when(io.need_flush){
    dispatch_info.init()
    dispatch_valid:=false.B
  }

  when(reset.asBool()){
    dispatch_info.init()
  }

}

