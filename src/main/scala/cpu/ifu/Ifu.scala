package cpu.ifu

import chisel3._
import chisel3.util._
import cpu.exu.FBRespInfo
import signal.Const._

class Ifu extends Module {
  val io = IO(new Bundle() {
    val ex_branch_info_i = Flipped(Valid(new BranchInfo))
    val fb_inst_bank_o   = Valid(new FBInstBank)
    val fb_resp = Input(new FBRespInfo())
    val icache_io_read_req = Decoupled(new ICacheReq)
    val icache_io_read_resp = Flipped(Decoupled(new ICacheResp))
    val icache_debug = new ICacheDebugIO
    val bpu_debug = new BpuDebugIO
  })

  val bpu          = Module(new BPU)
  val icache       = Module(new ICache)
  val fetch_buffer = Module(new FetchBuffer)

  val pc            = RegInit("h80000000".U(32.W))
  val no_taken_addr = Cat((pc(31, ICACHE_INST_WIDTH+2) + 1.U).asUInt(), 0.U((ICACHE_INST_WIDTH+2).W))
  val stop_fetch = !fetch_buffer.io.bpu_inst_packet_i.ready || !icache.io.icache_req.ready || !bpu.io.resp_o.valid
  val redirect = io.ex_branch_info_i.valid && io.ex_branch_info_i.bits.predict_miss

  val taking_delay      = RegInit(false.B)
  val delay_target_addr = RegInit(0.U(32.W))

  when(redirect) {
    pc := io.ex_branch_info_i.bits.target_addr
    taking_delay := false.B
  }.elsewhen(stop_fetch){
    pc :=pc
  }.elsewhen(taking_delay) {
    pc := delay_target_addr
    taking_delay := false.B
  }.elsewhen(bpu.io.resp_o.bits.take_delay) {
    pc := no_taken_addr
    taking_delay := true.B
    delay_target_addr := bpu.io.resp_o.bits.predict_addr
  }.elsewhen(bpu.io.resp_o.bits.is_taken) {
    pc := bpu.io.resp_o.bits.predict_addr
  }.otherwise {
    pc := no_taken_addr
  }


  icache.io.icache_req.bits.addr := pc
  icache.io.icache_req.valid := !redirect
  icache.io.icache_resp <> bpu.io.inst_packet_i
  icache.io.io_read_req<>io.icache_io_read_req
  icache.io.io_read_resp<>io.icache_io_read_resp

  bpu.io.is_delay:=taking_delay
  bpu.io.bpu_inst_packet_o <> fetch_buffer.io.bpu_inst_packet_i
  bpu.io.branch_info_i <> io.ex_branch_info_i
  bpu.io.need_flush:=redirect

  fetch_buffer.io.clear_i:=redirect
  io.fb_inst_bank_o <> fetch_buffer.io.inst_bank
  io.fb_resp<>fetch_buffer.io.fb_resp
  //debug
  io.icache_debug<>icache.io.icache_debug
  io.bpu_debug<>bpu.io.bpu_debug
}
