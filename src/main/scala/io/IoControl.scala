package io
//TODO 保持base_ram一致性
import chisel3._
import chisel3.util._
import cpu.exu.RobCommitInfo
import cpu.exu.unit.{DCacheReadReq, DCacheResp, DCacheWriteReq}
import cpu.ifu.{ICacheReq, ICacheResp}
import signal.Const._

class SramCtrlInfo extends Bundle {
  def idle(): Unit = {
    data_out := 0.U
    addr := 0.U
    be_n := "b1111".U
    ce_n := true.B
    oe_n := true.B
    we_n := true.B
  }

  def read(rAddr: UInt): Unit = {
    data_out := 0.U
    addr := rAddr
    be_n := "b0000".U
    ce_n := false.B
    oe_n := false.B
    we_n := true.B
  }

  def write(wAddr: UInt, wData: UInt, wBe_n: UInt): Unit = {
    data_out := wData
    addr := wAddr
    be_n := wBe_n
    ce_n := false.B
    oe_n := true.B
    we_n := false.B
  }

  val data_out = UInt(32.W)
  val addr     = UInt(20.W)
  val be_n     = UInt(4.W)
  val ce_n     = Bool()
  val oe_n     = Bool()
  val we_n     = Bool()
}

class SramCtrlIO extends Bundle {
  val data_in = Input(UInt(32.W))
  val ctrl    = Output(new SramCtrlInfo)
}

class IoControlDebugIO extends Bundle {
  val base_state        = Output(UInt(3.W))
  val icache_read_base  = Output(Bool())
  val icache_read_ext   = Output(Bool())
  val dcache_read_base  = Output(Bool())
  val dcache_read_ext   = Output(Bool())
  val dcache_write_base = Output(Bool())
  val dcache_write_ext  = Output(Bool())
  val icache_read_addr  = Output(UInt(20.W))
  val dcache_read_addr  = Output(UInt(20.W))
  val dcache_write_addr = Output(UInt(20.W))
}

class RxDIO extends Bundle{
  val uart_ready = Input(Bool())
  val uart_clear = Output(Bool())
  val uart_data = Input(UInt(8.W))
}
class TxDIO extends Bundle{
  val uart_start = Output(Bool())
  val uart_data = Output(UInt(8.W))
  val uart_busy = Input(Bool())
}

class UartBufferInfo extends Bundle{
  val data = UInt(8.W)
  val rob_idx = UInt(ROB_IDX_WIDTH.W)
}

class IoControlIO extends Bundle {
  val icache_read_req  = Flipped(Decoupled(new ICacheReq))
  val icache_read_resp = Decoupled(new ICacheResp)
  val dcache_read_req  = Flipped(Decoupled(new DCacheReadReq))
  val dcache_read_resp = Decoupled(new DCacheResp)
  val dcache_write_req = Flipped(Decoupled(new DCacheWriteReq))
  val base_ram_ctrl    = new SramCtrlIO
  val ext_ram_ctrl     = new SramCtrlIO
  val rxd = new RxDIO
  val txd = new TxDIO
  val rob_commit = Vec(COMMIT_WIDTH,Flipped(Valid(new RobCommitInfo())))
  val need_flush    = Input(Bool())
  val debug            = new IoControlDebugIO
}

class IoControl extends Module {
  def EndianConvert(data: UInt) = {
    //val w = data.getWidth
    //VecInit((0 until w / 8).map(i => data(i * 8 + 7, i * 8)).reverse).asUInt()

    // do nothing
    data
  }
  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }

  val io            = IO(new IoControlIO)
  val base_ram_ctrl = Reg(new SramCtrlInfo)
  val ext_ram_ctrl  = Reg(new SramCtrlInfo)
  io.base_ram_ctrl.ctrl <> base_ram_ctrl
  io.ext_ram_ctrl.ctrl <> ext_ram_ctrl

  val sIDLE :: iREAD :: dREAD :: dWrite :: iWait :: dWait :: Nil = Enum(6)
  val base_state                    = RegInit(sIDLE)
  val base_clock_counter            = RegInit(0.U(4.W))
  val base_wait_counter            = RegInit(0.U(4.W))
  val ext_state                     = RegInit(sIDLE)
  val ext_clock_counter             = RegInit(0.U(4.W))
  val ext_wait_counter             = RegInit(0.U(4.W))

  val icache_read_base              = io.icache_read_req.bits.addr(31,22)==="b1000_0000_00".U(10.W) && io.icache_read_req.valid
  val icache_read_ext               = io.icache_read_req.bits.addr(31,22)==="b1000_0000_01".U(10.W) && io.icache_read_req.valid
  val dcache_read_base              = io.dcache_read_req.bits.addr(31,22)==="b1000_0000_00".U(10.W) && io.dcache_read_req.valid
  val dcache_read_ext               = io.dcache_read_req.bits.addr(31,22)==="b1000_0000_01".U(10.W) && io.dcache_read_req.valid
  val dcache_write_base             = io.dcache_write_req.bits.addr(31,22)==="b1000_0000_00".U(10.W) && io.dcache_write_req.valid
  val dcache_write_ext              = io.dcache_write_req.bits.addr(31,22)==="b1000_0000_01".U(10.W) && io.dcache_write_req.valid
  val dcache_read_uart              = io.dcache_read_req.bits.addr === "hBFD003F8".U(32.W) && io.dcache_read_req.valid
  val dcache_write_uart              = io.dcache_write_req.bits.addr === "hBFD003F8".U(32.W) && io.dcache_write_req.valid
  val dcache_read_uart_state       = io.dcache_read_req.bits.addr === "hBFD003FC".U(32.W) && io.dcache_read_req.valid
  val icache_read_addr              = io.icache_read_req.bits.addr(21, 2)
  val dcache_read_addr              = io.dcache_read_req.bits.addr(21, 2)
  val dcache_write_addr             = io.dcache_write_req.bits.addr(21, 2)
  val icache_read_other = !icache_read_base&& !icache_read_ext && io.icache_read_req.valid
  val dcache_read_other = !dcache_read_base&& !dcache_read_ext && !dcache_read_uart && io.dcache_read_req.valid
  val dcache_write_other = !dcache_write_base&& !dcache_write_ext && !dcache_write_uart && io.dcache_write_req.valid
  //debug
  io.debug.base_state := base_state
  io.debug.icache_read_base := icache_read_base
  io.debug.icache_read_ext := icache_read_ext
  io.debug.dcache_read_base := dcache_read_base
  io.debug.dcache_read_ext := dcache_read_ext
  io.debug.dcache_write_base := dcache_write_base
  io.debug.dcache_write_ext := dcache_write_ext
  io.debug.icache_read_addr := icache_read_addr
  io.debug.dcache_read_addr := dcache_read_addr
  io.debug.dcache_write_addr := dcache_write_addr

  //pipe stage
  val icache_buffer     = RegInit(VecInit(Seq.fill(FETCH_WIDTH)(0.U(32.W))))
  val icache_data_valid = RegInit(false.B)
  io.icache_read_req.ready := icache_data_valid
  io.icache_read_resp.valid := icache_data_valid
  io.icache_read_resp.bits.data := icache_buffer.asUInt()
  val dcache_buffer     = RegInit(0.U(32.W))
  val dcache_data_valid = RegInit(false.B)
  val dcache_read_ready = WireInit(false.B)
  io.dcache_read_req.ready := dcache_read_ready
  io.dcache_read_resp.valid := dcache_data_valid
  io.dcache_read_resp.bits.data := dcache_buffer.asUInt()
  val dcache_write_complete = WireInit(false.B)
  io.dcache_write_req.ready := dcache_write_complete

  val oIDLE::oiWAIT::odWAIT::owWAIT::Nil = Enum(4)
  val other_state = RegInit(oIDLE)

  switch(other_state){
    is(oIDLE){
      when(icache_read_other){
        icache_buffer:=VecInit(Seq.fill(FETCH_WIDTH)(0.U(32.W)))
        icache_data_valid:=true.B
        other_state:=oiWAIT
      }
      when(dcache_read_other){
        dcache_buffer:=0.U(32.W)
        dcache_data_valid:=true.B
        dcache_read_ready :=true.B
        other_state:=odWAIT
      }
      when(dcache_write_other){
        dcache_write_complete:=true.B
        other_state:=owWAIT
      }
    }
    is(oiWAIT){
      icache_data_valid:=false.B
      other_state:=oIDLE
    }
    is(odWAIT){
      dcache_data_valid:=false.B
      other_state:=oIDLE
    }
    is(owWAIT){
      other_state:=oIDLE
    }
  }



  //base_ram
  switch(base_state) {
    is(sIDLE) {
      when(dcache_write_base) {
        base_state := dWrite
        //TODO byte_mask Endian need convert?
        base_ram_ctrl.write(dcache_write_addr, EndianConvert(io.dcache_write_req.bits.data), io.dcache_write_req.bits.byte_mask.do_unary_~)
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }.elsewhen(dcache_read_base) {
        base_state := dREAD
        base_ram_ctrl.read(dcache_read_addr)
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }.elsewhen(icache_read_base) {
        base_state := iREAD
        base_ram_ctrl.read(icache_read_addr)
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }
    }
    is(iREAD) {
      when(!icache_read_base) {
        base_ram_ctrl.idle()
        base_state := sIDLE
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }.elsewhen(base_clock_counter === (FETCH_WIDTH-1).U) {
        when(base_wait_counter === SRAM_DELAY.U){
          icache_buffer(base_clock_counter) := EndianConvert(io.base_ram_ctrl.data_in)
          base_ram_ctrl.idle()
          icache_data_valid := true.B
          base_state := iWait
          base_wait_counter:=0.U
        }.otherwise{
          base_wait_counter:=base_wait_counter+1.U
        }
      }.otherwise {
        when(base_wait_counter ===SRAM_DELAY.U){
          base_ram_ctrl.read(base_ram_ctrl.addr + 1.U)
          icache_buffer(base_clock_counter) := EndianConvert(io.base_ram_ctrl.data_in)
          base_clock_counter := base_clock_counter + 1.U
          base_wait_counter:=0.U
        }.otherwise{
          base_wait_counter:=base_wait_counter+1.U
        }
      }
    }
    is(iWait) {
        base_state := sIDLE
        icache_data_valid := false.B
    }
    is(dREAD) {
      when(!dcache_read_base) {
        base_ram_ctrl.idle()
        base_state := sIDLE
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }.elsewhen(base_wait_counter ===SRAM_DELAY.U){
        base_state := dWait
        base_ram_ctrl.idle()
        dcache_buffer := EndianConvert(io.base_ram_ctrl.data_in)
        dcache_data_valid := true.B
        dcache_read_ready :=true.B
      }.otherwise{
        base_wait_counter:= base_wait_counter+1.U
      }
    }
    is(dWait) {
        base_state := sIDLE
        dcache_data_valid := false.B
    }
    is(dWrite) {
      when(!dcache_write_base) {
        base_ram_ctrl.idle()
        base_state := sIDLE
        base_clock_counter := 0.U
        base_wait_counter :=0.U
      }.elsewhen(base_wait_counter ===SRAM_DELAY.U){
        base_state := sIDLE
        base_ram_ctrl.idle()
        dcache_write_complete := true.B
      }.otherwise{
        base_wait_counter:= base_wait_counter+1.U
      }
    }
  }
  //ext_ram
  switch(ext_state) {
    is(sIDLE) {
      when(dcache_read_ext) {
        ext_state := dREAD
        ext_ram_ctrl.read(dcache_read_addr)
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }.elsewhen(dcache_write_ext) {
        ext_state := dWrite
        //TODO byte_mask Endian need convert?
        ext_ram_ctrl.write(dcache_write_addr, EndianConvert(io.dcache_write_req.bits.data), io.dcache_write_req.bits.byte_mask.do_unary_~)
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }.elsewhen(icache_read_ext) {
        ext_state := iREAD
        ext_ram_ctrl.read(icache_read_addr)
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }
    }
    is(iREAD) {
      when(!icache_read_ext) {
        ext_ram_ctrl.idle()
        ext_state := sIDLE
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }.elsewhen(ext_clock_counter === (FETCH_WIDTH-1).U) {
        when(ext_wait_counter ===SRAM_DELAY.U){
          icache_buffer(ext_clock_counter) := EndianConvert(io.ext_ram_ctrl.data_in)
          ext_ram_ctrl.idle()
          icache_data_valid := true.B
          ext_state := iWait
          ext_wait_counter:=0.U
        }.otherwise{
          ext_wait_counter:=ext_wait_counter+1.U
        }
      }.otherwise {
        when(ext_wait_counter ===SRAM_DELAY.U){
          ext_ram_ctrl.read(ext_ram_ctrl.addr + 1.U)
          icache_buffer(ext_clock_counter) := EndianConvert(io.ext_ram_ctrl.data_in)
          ext_clock_counter := ext_clock_counter + 1.U
          ext_wait_counter:=0.U
        }.otherwise{
          ext_wait_counter:=ext_wait_counter+1.U
        }
      }
    }
    is(iWait) {
      ext_state := sIDLE
      icache_data_valid := false.B
    }
    is(dREAD) {
      when(!dcache_read_ext) {
        ext_ram_ctrl.idle()
        ext_state := sIDLE
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }.elsewhen(ext_wait_counter ===SRAM_DELAY.U){
        ext_state := dWait
        ext_ram_ctrl.idle()
        dcache_buffer := EndianConvert(io.ext_ram_ctrl.data_in)
        dcache_data_valid := true.B
        dcache_read_ready :=true.B
      }.otherwise{
        ext_wait_counter:= ext_wait_counter+1.U
      }
    }
    is(dWait) {
      ext_state := sIDLE
      dcache_data_valid := false.B
    }
    is(dWrite) {
      when(!dcache_write_ext) {
        ext_ram_ctrl.idle()
        ext_state := sIDLE
        ext_clock_counter := 0.U
        ext_wait_counter :=0.U
      }.elsewhen(ext_wait_counter ===SRAM_DELAY.U){
        ext_state := sIDLE
        ext_ram_ctrl.idle()
        dcache_write_complete := true.B
      }.otherwise{
        ext_wait_counter:= ext_wait_counter+1.U
      }
    }
  }



  val uart_buffer = Reg(Vec(UART_BUFFER_DEPTH,new UartBufferInfo))
  val uart_buffer_wait = RegInit(VecInit(Seq.fill(UART_BUFFER_DEPTH)(false.B)))
  val uart_head = RegInit(1.U(UART_BUFFER_DEPTH.W))
  val head_idx = OHToUInt(uart_head)
  val uart_flush_head = RegInit(1.U(UART_BUFFER_DEPTH.W))
  val uart_tail = RegInit(1.U(UART_BUFFER_DEPTH.W))
  val tail_idx = OHToUInt(uart_tail)
  val maybe_full = RegInit(false.B)
  val maybe_true_full = RegInit(false.B)
  val uart_full = uart_flush_head===uart_tail&& maybe_true_full
  val uart_empty = uart_head===uart_tail&& !maybe_full
  val write_req = WireInit(false.B)
  val write_ready = WireInit(false.B)
  val read_req = WireInit(false.B)
  val read_data = WireInit(0.U(8.W))
  val read_valid = WireInit(false.B)
  val read_rob_idx = WireInit(0.U(ROB_IDX_WIDTH.W))
  val write_data = WireInit(0.U(8.W))
  val uart_enq = !uart_full&&write_req
  val uart_deq = !uart_empty&&read_req

  read_data:=uart_buffer(head_idx).data
  read_valid:= !uart_empty
  write_ready:= !uart_full

  when(uart_enq){
    uart_buffer(tail_idx).data:=write_data
    uart_buffer(tail_idx).rob_idx:=0.U
    uart_tail:=leftRotate(uart_tail,1)
  }

  when(uart_deq) {
    uart_buffer(head_idx).rob_idx:=read_rob_idx
    uart_buffer_wait(head_idx):=true.B
    uart_head:=leftRotate(uart_head,1)
  }


  val will_drop = WireInit(VecInit(Seq.fill(COMMIT_WIDTH)(false.B)))
  for(i<-0 until COMMIT_WIDTH){
    val flush_head_idx = OHToUInt(leftRotate(uart_flush_head,i))
    for(j<-0 until COMMIT_WIDTH){
      when(uart_buffer(flush_head_idx).rob_idx===io.rob_commit(j).bits.des_rob&&io.rob_commit(j).valid&&uart_buffer_wait(flush_head_idx)){
        uart_buffer(flush_head_idx).rob_idx:=0.U
        uart_buffer_wait(flush_head_idx):=false.B
        will_drop(i):=true.B
      }
    }
  }
  val next_flush_head = (0 until COMMIT_WIDTH).foldLeft(uart_flush_head)((p,i)=>Mux(will_drop(i),leftRotate(uart_flush_head,i+1),p))
  uart_flush_head:=next_flush_head

  when(uart_enq){
    maybe_true_full:=true.B
  }.elsewhen(will_drop.reduce(_|_)){
    maybe_true_full:=false.B
  }
  when(uart_enq){
    maybe_full:=true.B
  }.elsewhen(uart_deq){
    maybe_full:=false.B
  }

  when(io.need_flush){
    uart_head:=next_flush_head
    for(i<- 0 until UART_BUFFER_DEPTH){
      uart_buffer_wait(i):=false.B
    }
    maybe_full:=maybe_true_full
  }



  val uIDLE::uREAD::uWRITE::Nil = Enum(3)
  val uart_state = RegInit(uIDLE)
  val txd_uart_start = RegInit(false.B)
  val txd_uart_data = RegInit(0.U(8.W))
  io.txd.uart_start:=txd_uart_start
  io.txd.uart_data:=txd_uart_data

  when(io.rxd.uart_ready&& write_ready){
    write_data:=io.rxd.uart_data
    write_req:=true.B
    io.rxd.uart_clear:=true.B
  }.otherwise{
    io.rxd.uart_clear:=false.B
    write_req:=false.B
    write_data:=DontCare
  }

  switch(uart_state){
    is(uIDLE){
      when(dcache_write_uart&& !io.txd.uart_busy){
        txd_uart_start:=true.B
        txd_uart_data:=io.dcache_write_req.bits.data(7,0)
        dcache_write_complete:=true.B
        uart_state:=uWRITE
      }.elsewhen(dcache_read_uart&& read_valid){
        dcache_buffer:=Cat(0.U(24.W),read_data)
        dcache_data_valid:=true.B
        dcache_read_ready :=true.B
        read_req:=true.B
        read_rob_idx:=io.dcache_read_req.bits.rob_idx
        uart_state:=uREAD
      }.elsewhen(dcache_read_uart_state){
        dcache_buffer:=Cat(0.U(30.W),read_valid,!io.txd.uart_busy)
        dcache_data_valid:=true.B
        dcache_read_ready :=true.B
        uart_state:=uREAD
      }
    }
    is(uWRITE){
      txd_uart_start:=false.B
      uart_state:=uIDLE
    }
    is(uREAD){
      dcache_data_valid:=false.B
      uart_state:=uIDLE
    }
  }


  when(reset.asBool()) {
    base_ram_ctrl.idle()
    ext_ram_ctrl.idle()
    uart_buffer.foreach(i=>{
      i.rob_idx:=0.U
      i.data:=0.U
    })
  }

}
