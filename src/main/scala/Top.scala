import chisel3._
import chisel3.util._
import cpu.Core
import cpu.ifu.{BpuDebugIO, ICacheDebugIO}
import io.{IoControl, IoControlDebugIO, RxDIO, SramCtrlIO, TxDIO}

class Top extends Module{
  val io = IO(new Bundle() {
    val base_ram_ctrl         = new SramCtrlIO
    val ext_ram_ctrl          = new SramCtrlIO
    val rxd = new RxDIO
    val txd = new TxDIO
    val io_control_debug = new IoControlDebugIO
    val icache_debug = new ICacheDebugIO
    val bpu_debug = new BpuDebugIO
  })
  val core = Module(new Core)
  val io_control = Module(new IoControl)
  core.io.icache_io_read_req <>io_control.io.icache_read_req
  core.io.icache_io_read_resp<>io_control.io.icache_read_resp
  core.io.dcache_io_read_req <>io_control.io.dcache_read_req
  core.io.dcache_io_read_resp<>io_control.io.dcache_read_resp
  core.io.dcache_io_write_req<>io_control.io.dcache_write_req
  core.io.need_flush<>io_control.io.need_flush
  core.io.rob_commit<>io_control.io.rob_commit

  io.ext_ram_ctrl<>io_control.io.ext_ram_ctrl
  io.base_ram_ctrl<>io_control.io.base_ram_ctrl
  io.rxd<>io_control.io.rxd
  io.txd<>io_control.io.txd
  //debug
  io.io_control_debug<>io_control.io.debug
  io.icache_debug<>core.io.icache_debug
  io.bpu_debug<>core.io.bpu_debug
}
