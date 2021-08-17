package cpu.exu

import chisel3._
import chisel3.util._
import chisel3.experimental._
import cpu.exu.unit.{Alu, Bju, DCacheReadReq, DCacheResp, DCacheWriteReq, Lsu, Mdu}
import cpu.ifu.{BranchInfo, FBInstBank}
import instructions.MIPS32._
import signal.Const.COMMIT_WIDTH
import signal._


class Exu extends Module{
  val io = IO(new Bundle{
    val fb_inst_bank_i = Flipped(Valid(new FBInstBank))
    val fb_resp = Output(new FBRespInfo())
    val ex_branch_info_o = Valid(new BranchInfo)
    val dcache_io_read_req  = Decoupled(new DCacheReadReq)
    val dcache_io_read_resp = Flipped(Decoupled(new DCacheResp))
    val dcache_io_write_req = Decoupled(new DCacheWriteReq)
    val need_flush      = Output(Bool())
    val rob_commit      = Vec(COMMIT_WIDTH, Valid(new RobCommitInfo))
  })
  val decode = Module(new Decode)
  val rename = Module(new Rename)
  val regfile = Module(new Regfile)
  val rob = Module(new Rob)
  val issue = Module(new Issue)
  val alu0 = Module(new Alu)
  val alu1 = Module(new Alu)
  val bju0 = Module(new Bju)
  val lsu = Module(new Lsu)
  val mdu = Module(new Mdu)
  val dcache = Module(new Dcache)

  io.fb_inst_bank_i<>decode.io.fb_inst_bank
  decode.io.fb_resp<>io.fb_resp
  decode.io.rob_allocate<>rob.io.rob_allocate
  decode.io.decode_info<>rename.io.decode_info

  rename.io.reg_read<>regfile.io.reg_read
  rename.io.rob_commit<>rob.io.rob_commit
  rename.io.rename_info<>issue.io.rename_info

  issue.io.rob_read<>rob.io.rob_read


  regfile.io.rob_commit_i<>rob.io.rob_commit
  io.ex_branch_info_o<>rob.io.branch_info
  lsu.io.rob_commit<>rob.io.rob_commit
  lsu.io.cache_read <>dcache.io.dcache_read_req
  lsu.io.cache_write<>dcache.io.dcache_write_req
  lsu.io.cache_resp <>dcache.io.dcache_read_resp
  dcache.io.io_read_req <>io.dcache_io_read_req
  dcache.io.io_read_resp<>io.dcache_io_read_resp
  dcache.io.io_write_req<>io.dcache_io_write_req

  rob.io.need_flush<>io.need_flush
  rob.io.need_flush<>decode.io.need_flush
  rob.io.need_flush<>rename.io.need_flush
  rob.io.need_flush<>issue.io.need_flush
  rob.io.need_flush<>alu0.io.need_flush
  rob.io.need_flush<>alu1.io.need_flush
  rob.io.need_flush<>bju0.io.need_flush
  rob.io.need_flush<>lsu.io.need_flush
  rob.io.need_flush<>mdu.io.need_flush


  rob.io.wb_info_i(0)<>alu0.io.wb_info
  rob.io.wb_info_i(1)<>alu1.io.wb_info
  rob.io.wb_info_i(2)<>bju0.io.wb_info
  rob.io.wb_info_i(3)<>mdu.io.wb_info
  rob.io.wb_info_i(4)<>lsu.io.wb_info

  issue.io.wb_info(0)<>alu0.io.wb_info
  issue.io.wb_info(1)<>alu1.io.wb_info
  issue.io.wb_info(2)<>bju0.io.wb_info
  issue.io.wb_info(3)<>mdu.io.wb_info
  issue.io.wb_info(4)<>lsu.io.wb_info

  issue.io.dispatch_info(0)<>alu0.io.dispatch_info
  issue.io.dispatch_info(1)<>alu1.io.dispatch_info
  issue.io.dispatch_info(2)<>bju0.io.dispatch_info
  issue.io.dispatch_info(3)<>mdu.io.dispatch_info
  issue.io.dispatch_info(4)<>lsu.io.dispatch_info

  issue.io.need_stop<>decode.io.need_stop
  issue.io.need_stop<>rename.io.need_stop
  issue.io.need_stop<>rob.io.need_stop


  rob.io.rob_commit<>io.rob_commit

}
