package cpu

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.unit.{DCacheReadReq, DCacheResp, DCacheWriteReq}
import cpu.exu.{DispatchInfo, Exu, RobCommitInfo, WriteBackInfo}
import cpu.ifu.{BpuDebugIO, BranchInfo, FBInstBank, ICacheDebugIO, ICacheReq, ICacheResp, Ifu}
import instructions.MIPS32._
import signal.Const.COMMIT_WIDTH
import signal._

class Core extends Module{
  val io = IO(new Bundle() {
    val icache_io_read_req = Decoupled(new ICacheReq)
    val icache_io_read_resp = Flipped(Decoupled(new ICacheResp))
    val dcache_io_read_req  = Decoupled(new DCacheReadReq)
    val dcache_io_read_resp = Flipped(Decoupled(new DCacheResp))
    val dcache_io_write_req = Decoupled(new DCacheWriteReq)
    val need_flush      = Output(Bool())
    val rob_commit      = Vec(COMMIT_WIDTH, Valid(new RobCommitInfo))
    val icache_debug = new ICacheDebugIO
    val bpu_debug = new BpuDebugIO
  })
  val ifu = Module(new Ifu)
  val exu = Module(new Exu)
  exu.io.fb_inst_bank_i<>ifu.io.fb_inst_bank_o
  exu.io.fb_resp<>ifu.io.fb_resp
  ifu.io.ex_branch_info_i<>exu.io.ex_branch_info_o

  io.icache_io_read_req<>ifu.io.icache_io_read_req
  io.icache_io_read_resp<>ifu.io.icache_io_read_resp

  io.dcache_io_read_req <>exu.io.dcache_io_read_req
  io.dcache_io_read_resp<>exu.io.dcache_io_read_resp
  io.dcache_io_write_req<>exu.io.dcache_io_write_req

  io.need_flush<>exu.io.need_flush
  io.rob_commit<>exu.io.rob_commit

  //debug
  io.icache_debug<>ifu.io.icache_debug
  io.bpu_debug<>ifu.io.bpu_debug

}
