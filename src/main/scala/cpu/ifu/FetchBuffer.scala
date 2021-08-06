package cpu.ifu

import chisel3.{Bool, _}
import chisel3.util._
import cpu.exu.FBRespInfo
import signal.Const._

class FbInstInfo extends Bundle {
  val inst          = UInt(32.W)
  val inst_addr     = UInt(32.W)
  val gh_backup     = UInt(4.W)
  val is_valid      = Bool()
  val is_delay      = Bool()
  val is_branch     = Bool()
  val predict_taken = Bool()
  def init(): Unit = {
    inst          := 0.U(32.W)
    inst_addr     := 0.U(32.W)
    gh_backup     := 0.U(4.W)
    is_valid      := false.B
    is_delay      := false.B
    is_branch     := false.B
  }
}

class FBInstBank extends Bundle {
  val data = Vec(ISSUE_WIDTH, new FbInstInfo)
}

class FetchBufferIO extends Bundle {
  val bpu_inst_packet_i = Flipped(Decoupled(new BpuInstPacket))
  val inst_bank         = Valid(new FBInstBank)
  val fb_resp = Input(new FBRespInfo())
  val clear_i           = Input(Bool())
}

class FetchBuffer extends Module {

  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }

  val io           = IO(new FetchBufferIO)
  val fetch_buffer = Reg(Vec(FETCH_BUFFER_DEPTH, new FbInstInfo))
  val head         = RegInit(1.U(FETCH_BUFFER_DEPTH.W))
  val tail         = RegInit(1.U(FETCH_BUFFER_DEPTH.W))
  val maybe_full   = RegInit(false.B)
  val inst_packet  = Wire(Vec(FETCH_WIDTH, new FbInstInfo))
  val hit_head     = head === tail
  val is_full      = hit_head && maybe_full
  val is_empty     = hit_head && !maybe_full
  //enqueue logic
  val enq_idxs     = Wire(Vec(FETCH_WIDTH, UInt(FETCH_BUFFER_DEPTH.W)))

  val might_hit_head = enq_idxs.map(_ === head).reduce(_ | _)
  //tail will change
  val do_enq         = (is_empty || !(is_full || might_hit_head)) && io.bpu_inst_packet_i.valid && io.bpu_inst_packet_i.bits.valid_mask.reduce(_ | _)

  var enq_idx = tail
  for (i <- 0 until FETCH_WIDTH) {
    enq_idxs(i) := enq_idx
    enq_idx = Mux(io.bpu_inst_packet_i.bits.valid_mask(i), leftRotate(enq_idx, 1), enq_idx)
  }


  for (i <- 0 until FETCH_WIDTH) {
    inst_packet(i).inst := io.bpu_inst_packet_i.bits.data(i)
    inst_packet(i).inst_addr := Cat(io.bpu_inst_packet_i.bits.addr(31, ICACHE_INST_WIDTH+2), i.U(ICACHE_INST_WIDTH.W), 0.U(2.W))
    inst_packet(i).is_branch := io.bpu_inst_packet_i.bits.branch_mask(i)
    inst_packet(i).is_delay := io.bpu_inst_packet_i.bits.delay_mask(i)
    inst_packet(i).predict_taken := io.bpu_inst_packet_i.bits.predict_mask(i)
    inst_packet(i).is_valid := io.bpu_inst_packet_i.bits.valid_mask(i)
    inst_packet(i).gh_backup := io.bpu_inst_packet_i.bits.gh_backup
  }

  for (i <- 0 until FETCH_WIDTH) {
    for (j <- 0 until FETCH_BUFFER_DEPTH) {
      when(do_enq && inst_packet(i).is_valid && enq_idxs(i)(j)) {
        fetch_buffer(j) := inst_packet(i)
      }
    }
  }
  when(do_enq) {
    tail := enq_idx
  }


  //dequeue logic
  io.inst_bank.bits.data := DontCare
  val deq_idxs      = (0 until ISSUE_WIDTH).map(i => leftRotate(head, i))
  val hit_tail_mask = deq_idxs.map(_ === tail && !is_full)
  val deq_invalid   = hit_tail_mask.scanLeft(false.B)(_ | _).drop(1)
  val will_hit_tail = hit_tail_mask.reduce(_ | _)
  val do_deq        = io.fb_resp.deq_valid.reduce(_|_)
  for (i <- 0 until ISSUE_WIDTH) {
    for (j <- 0 until FETCH_BUFFER_DEPTH) {
      when(deq_idxs(i)(j)) {
        io.inst_bank.bits.data(i) := fetch_buffer(j)
        io.inst_bank.bits.data(i).is_valid := !deq_invalid(i)
      }
    }
  }
  var next_head = head
  for (i<- 0 until ISSUE_WIDTH){
    next_head = Mux(io.fb_resp.deq_valid(i),leftRotate(next_head,1),next_head)
  }
  head:=next_head

  io.inst_bank.valid := !is_empty && !io.clear_i
  //update state
  when(do_deq && do_enq) {
    maybe_full := true.B
  }.elsewhen(do_deq) {
    maybe_full := false.B
  }.elsewhen(do_enq) {
    maybe_full := true.B
  }

  when(io.clear_i) {
    tail := 1.U
    head := 1.U
    maybe_full := false.B
  }

  io.bpu_inst_packet_i.ready := is_empty || !(is_full || might_hit_head) || !io.bpu_inst_packet_i.valid || !io.bpu_inst_packet_i.bits.valid_mask.reduce(_ | _)


  when(reset.asBool()) {
    fetch_buffer.foreach(i => i.init())
  }


}
