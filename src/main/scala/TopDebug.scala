import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import cpu.Core
import io.{IoControl, RxDIO, SramCtrlIO, TxDIO}

class TopDebug extends Module{
  val io = IO(new Bundle() {
    val base_ram_ctrl         =new SramCtrlIO
    val ext_ram_ctrl          =new SramCtrlIO
    val rxd = new RxDIO
    val txd = new TxDIO
  })
  val top = Module(new Top)
  val base_ram = Mem(1024, UInt(32.W))
  val ext_ram = Mem(1024, UInt(32.W))
  loadMemoryFromFile(base_ram, "C:\\Users\\Alwen\\Desktop\\mars\\test.txt")

  io.base_ram_ctrl<>top.io.base_ram_ctrl
  io.ext_ram_ctrl<>top.io.ext_ram_ctrl
  io.rxd<>top.io.rxd
  io.txd<>top.io.txd
  when( !top.io.base_ram_ctrl.ctrl.ce_n){
    when(top.io.base_ram_ctrl.ctrl.we_n&& !top.io.base_ram_ctrl.ctrl.oe_n){
      top.io.base_ram_ctrl.data_in:=base_ram.read(top.io.base_ram_ctrl.ctrl.addr).asUInt()
    }.elsewhen( !reset.asBool()){
      base_ram.write(top.io.base_ram_ctrl.ctrl.addr,top.io.base_ram_ctrl.ctrl.data_out)
    }
  }
  when( !top.io.ext_ram_ctrl.ctrl.ce_n){
    when(top.io.ext_ram_ctrl.ctrl.we_n&& !top.io.ext_ram_ctrl.ctrl.oe_n){
      top.io.ext_ram_ctrl.data_in:=ext_ram.read(top.io.ext_ram_ctrl.ctrl.addr).asUInt()
    }.elsewhen( !reset.asBool()){
      ext_ram.write(top.io.ext_ram_ctrl.ctrl.addr,top.io.ext_ram_ctrl.ctrl.data_out)
    }
  }

}
