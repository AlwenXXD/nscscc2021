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
  val alu0 = Module(new Alu)
  val alu1 = Module(new Alu)
//  val alu2 = Module(new Alu)
  val bju0 = Module(new Bju)
//  val bju1 = Module(new Bju)
  val lsu = Module(new Lsu)
  val mdu = Module(new Mdu)
  val dcache = Module(new Dcache)

  io.fb_inst_bank_i<>decode.io.fb_inst_bank
  decode.io.fb_resp<>io.fb_resp
  rob.io.rob_allocate<>decode.io.rob_allocate
  rename.io.rename_info<>decode.io.rename_info
  rename.io.reg_read<>regfile.io.reg_read
  rob.io.rob_init_info<>rename.io.rob_init_info
  rename.io.rob_commit<>rob.io.rob_commit

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
  rob.io.need_flush<>alu0.io.need_flush
  rob.io.need_flush<>alu1.io.need_flush
//  rob.io.need_flush<>alu2.io.need_flush
  rob.io.need_flush<>bju0.io.need_flush
//  rob.io.need_flush<>bju1.io.need_flush
  rob.io.need_flush<>lsu.io.need_flush
  rob.io.need_flush<>mdu.io.need_flush


  rob.io.wb_info_i(0)<>alu0.io.wb_info
  rob.io.wb_info_i(1)<>alu1.io.wb_info
//  rob.io.wb_info_i(2)<>alu2.io.wb_info
  rob.io.wb_info_i(2)<>bju0.io.wb_info
//  rob.io.wb_info_i(4)<>bju1.io.wb_info
  rob.io.wb_info_i(3)<>mdu.io.wb_info
  rob.io.wb_info_i(4)<>lsu.io.wb_info

  rob.io.dispatch_info_o(0)<>alu0.io.dispatch_info
  rob.io.dispatch_info_o(1)<>alu1.io.dispatch_info
//  rob.io.dispatch_info_o(2)<>alu2.io.dispatch_info
  rob.io.dispatch_info_o(2)<>bju0.io.dispatch_info
//  rob.io.dispatch_info_o(4)<>bju1.io.dispatch_info
  rob.io.dispatch_info_o(3)<>mdu.io.dispatch_info
  rob.io.dispatch_info_o(4)<>lsu.io.dispatch_info


  rob.io.rob_commit<>io.rob_commit

}
