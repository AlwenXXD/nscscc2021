package cpu.exu.unit

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.{DispatchInfo, WriteBackInfo}
import cpu.ifu.FBInstBank
import instructions.MIPS32._
import signal._

class AluIO extends Bundle {
  val dispatch_info = Flipped(Decoupled(new DispatchInfo()))
  val wb_info       = Valid(new WriteBackInfo())
  val need_flush    = Input(Bool())
}

class Alu extends Module {
  val io          = IO(new AluIO)
  val dispatch_info         = Reg(new DispatchInfo)
  dispatch_info :=io.dispatch_info.bits
  val dispatch_valid        = RegInit(false.B)
  dispatch_valid:=io.dispatch_info.valid
  io.dispatch_info.ready:=true.B


  val op1_data    = dispatch_info.op1_data
  val op2_data    = Mux(dispatch_info.need_imm, dispatch_info.imm_data,dispatch_info.op2_data)
  val result_data = Wire(UInt(32.W))

  result_data := 0.U
  switch(dispatch_info.uop) {
    is(uOP.Alu_ADD) {
      result_data := op1_data + op2_data
    }
    is(uOP.Alu_ADDU) {
      result_data := op1_data + op2_data
    }
    is(uOP.Alu_SUB) {
      result_data := op1_data - op2_data
    }
    is(uOP.Alu_SUBU) {
      result_data := op1_data - op2_data
    }
    is(uOP.Alu_SLL) {
      result_data := op1_data.asUInt() << op2_data(4,0)
    }
    is(uOP.Alu_SRL) {
      result_data := (op1_data.asUInt() >> op2_data(4,0)).asUInt()
    }
    is(uOP.Alu_SRA) {
      result_data := (op1_data.asSInt() >> op2_data).asUInt()
    }
    is(uOP.Alu_SLT) {
      result_data := (op1_data.asSInt() < op2_data.asSInt()).asUInt()
    }
    is(uOP.Alu_SLTU) {
      result_data := op1_data.asUInt() < op2_data.asUInt()
    }
    is(uOP.Alu_XOR) {
      result_data := op1_data ^ op2_data
    }
    is(uOP.Alu_OR) {
      result_data := op1_data | op2_data
    }
    is(uOP.Alu_AND) {
      result_data := op1_data & op2_data
    }
    is(uOP.Alu_LUI) {
      result_data := Cat(op2_data(15,0),0.U(16.W))
    }
    is(uOP.Alu_NOR) {
      result_data := (op1_data | op2_data).do_unary_~
    }
  }
  val wb_info       = Reg(new WriteBackInfo())
  val wb_info_valid       = RegInit(false.B)
//  wb_info.data:=result_data
//  wb_info.rob_idx:=dispatch_info.rob_idx
//  wb_info.is_taken:=false.B
//  wb_info.target_addr:=0.U
//  wb_info.predict_miss:=false.B
//  wb_info_valid:=dispatch_valid
//
//  io.wb_info.bits:=wb_info
//  io.wb_info.valid:=wb_info_valid

  io.wb_info.bits.data:=result_data
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