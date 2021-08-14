package cpu.exu

import chisel3._
import chisel3.util.{Decoupled, Valid}
import cpu.exu.unit.{DCacheReadReq, DCacheResp, DCacheWriteReq}



class DcacheIO extends Bundle{
  val dcache_read_req     = Flipped(Decoupled(new DCacheReadReq))
  val dcache_read_resp    = Valid(new DCacheResp)
  val dcache_write_req     = Flipped(Decoupled(new DCacheWriteReq))
  val io_read_req  = Decoupled(new DCacheReadReq)
  val io_read_resp = Flipped(Decoupled(new DCacheResp))
  val io_write_req = Decoupled(new DCacheWriteReq)
}

class Dcache extends Module{
  val io = IO(new DcacheIO)

  io.io_read_req<>io.dcache_read_req
  io.io_read_resp.bits<>io.dcache_read_resp.bits
  io.io_read_resp.valid<>io.dcache_read_resp.valid
  io.io_read_resp.ready:=true.B
  io.io_write_req<>io.dcache_write_req


}
