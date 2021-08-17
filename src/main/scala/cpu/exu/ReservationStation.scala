package cpu.exu

import chisel3.{UInt, Vec, _}
import chisel3.util.{Decoupled, _}
import chisel3.experimental._
import cpu.ifu.{BranchInfo, FBInstBank}
import instructions.MIPS32._
import signal.Const._
import signal._

import scala.:+

class RSInfo extends Bundle{
  val is_valid        = Bool()
  val rob_idx         = UInt(ROB_IDX_WIDTH.W)
  val uop             = uOP()
  val need_imm        = Bool()
  val inst_addr       = UInt(32.W)
  val op1_ready       = Bool()
  val op1_tag         = UInt(ROB_IDX_WIDTH.W)
  val op1_data        = UInt(32.W)
  val op2_ready       = Bool()
  val op2_tag         = UInt(ROB_IDX_WIDTH.W)
  val op2_data        = UInt(32.W)
  val imm_data        = UInt(32.W)
  val predict_taken   = Bool()
  def init(): Unit ={
    is_valid        := false.B
    rob_idx         := 0.U(ROB_IDX_WIDTH.W)
    uop             := uOP.NOP
    need_imm        := false.B
    inst_addr       := 0.U(32.W)
    op1_ready       := false.B
    op1_tag         := 0.U(ROB_IDX_WIDTH.W)
    op1_data        := 0.U(32.W)
    op2_ready       := false.B
    op2_tag         := 0.U(ROB_IDX_WIDTH.W)
    op2_data        := 0.U(32.W)
    imm_data        := 0.U(32.W)
    predict_taken   := false.B
  }
}

class RSIO extends Bundle{
  val issue_info    = Flipped(Decoupled(Vec(ISSUE_WIDTH, new RSInfo)))
  val dispatch_info = Decoupled(new DispatchInfo)
  val wb_info    = Vec(5, Flipped(Valid(new WriteBackInfo)))
  val need_flush = Input(Bool())
  val need_stop = Input(Bool())
}

class ReservationStation(entry:Int) extends Module{
  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }

  val io = IO(new RSIO)
  val queue      = Reg(Vec(entry, new RSInfo))
  val head       = RegInit(1.U(entry.W))
  val tail       = RegInit(1.U(entry.W))
  val maybe_full = RegInit(false.B)
  val full = head === tail && maybe_full
  val empty = head === tail && !maybe_full


  val enq_idxs = Wire(Vec(ISSUE_WIDTH,UInt(entry.W)))
  var enq_idx = tail
  for (i<-0 until ISSUE_WIDTH){
    enq_idxs(i) :=enq_idx
    enq_idx = Mux(io.issue_info.valid&&io.issue_info.bits(i).is_valid,leftRotate(enq_idx,1),enq_idx)
  }
  tail:= Mux(io.need_stop,tail,enq_idx)
  val will_hit_head = enq_idxs.map(_===head).reduce(_|_)&& !empty
  val do_enq = !will_hit_head && io.issue_info.valid && !io.need_stop && io.issue_info.bits.map(_.is_valid).reduce(_|_)
  io.issue_info.ready := !will_hit_head || !io.issue_info.valid

  for(i<-0 until ISSUE_WIDTH){
    val queue_idx = OHToUInt(enq_idxs(i))
    when( !io.need_stop&&io.issue_info.bits(i).is_valid&&io.issue_info.valid){
      queue(queue_idx):=  io.issue_info.bits(i)
    }
  }

  val head_info = queue(OHToUInt(head))
  val head_ready = head_info.op1_ready && head_info.op2_ready
  val do_deq = io.dispatch_info.ready && !empty && head_ready

  io.dispatch_info.valid                := !empty && head_ready
  io.dispatch_info.bits.uop             := head_info.uop
  io.dispatch_info.bits.need_imm        := head_info.need_imm
  io.dispatch_info.bits.rob_idx         := head_info.rob_idx
  io.dispatch_info.bits.inst_addr       := head_info.inst_addr
  io.dispatch_info.bits.op1_data        := head_info.op1_data
  io.dispatch_info.bits.op2_data        := head_info.op2_data
  io.dispatch_info.bits.imm_data        := head_info.imm_data
  io.dispatch_info.bits.predict_taken   := head_info.predict_taken
  when(do_deq){
    head:=leftRotate(head,1)
    queue(OHToUInt(head)).init()
  }

  when(do_deq){
    maybe_full:=false.B
  }.elsewhen(do_enq){
    maybe_full:=true.B
  }

  for (i <- 0 until entry){
    for ( j<- 0 until 5){
      when(queue(i).is_valid && !queue(i).op1_ready && io.wb_info(j).valid && queue(i).op1_tag === io.wb_info(j).bits.rob_idx&& !io.need_flush){
        queue(i).op1_ready := true.B
        queue(i).op1_data := io.wb_info(j).bits.data
      }
      when(queue(i).is_valid && !queue(i).op2_ready && io.wb_info(j).valid && queue(i).op2_tag === io.wb_info(j).bits.rob_idx&& !io.need_flush){
        queue(i).op2_ready := true.B
        queue(i).op2_data := io.wb_info(j).bits.data
      }
    }
  }

  when(io.need_flush){
    queue.foreach(_.init())
    head := 1.U(entry.W)
    tail := 1.U(entry.W)
    maybe_full := false.B
  }

  when(reset.asBool()){
    queue.foreach(_.init())
  }
}
