# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_win32.rb, before the game's scripts.
# What a script measures of the screen and its window through Win32API, answered as on a PC whose screen is the
# phone's: GetSystemMetrics, GetDeviceCaps, SystemParametersInfo (work area) and GetMonitorInfo give the phone's
# display; GetClientRect and GetWindowRect give the game's own size at 0, 0, for any window; the window has no border
# or title bar. They gave 0, or left the rectangle at 0, and a full-screen script (Fullscreen++ and the like) divided
# by it: 0.0 / 0 is NaN, and NaN.to_i stopped the game (report of 04/11/2026).
# Moving, sizing, showing or styling the window succeeds and does nothing: mkxp-z fills the phone's screen itself.
module AgvnScreen
  GAME = 42 # the game's window, as win32_wrap.rb's FindWindowA gives it
  DESKTOP = 0x10010
  MONITOR = 0x10001
  DC = 0x10002

  class << self
    def game
      [Graphics.width, Graphics.height]
    rescue StandardError
      [640, 480]
    end

    # The phone's display, turned as the game is; the game's own size when mkxp-z cannot tell
    def display
      gw, gh = game
      w = Graphics.respond_to?(:display_width) ? Graphics.display_width.to_i : 0
      h = Graphics.respond_to?(:display_height) ? Graphics.display_height.to_i : 0
      return [gw, gh] if w <= 0 || h <= 0
      (w >= h) == (gw >= gh) ? [w, h] : [h, w]
    rescue StandardError
      game
    end

    # Writes {bytes} into the buffer the game passed, from {at}, as far as the buffer goes; 1 when it could, else 0
    def fill(buf, bytes, at = 0)
      return 0 unless buf.is_a?(String) && !buf.frozen? && buf.bytesize > at
      [bytes.bytesize, buf.bytesize - at].min.times { |i| buf.setbyte(at + i, bytes.getbyte(i)) }
      1
    end

    def rect(buf, w, h, at = 0)
      fill(buf, [0, 0, w, h].pack('l4'), at)
    end

    # A window the game asks for by class or by title: its own, and no other program's
    def window(cls, title)
      cls = text(cls)
      title = text(title)
      return GAME if cls&.casecmp?('RGSS Player')
      return 0 unless cls.nil? && title
      own = %i[window_title game_title].map { |m| System.respond_to?(m) ? System.send(m).to_s.b : nil }
      own.include?(title) ? GAME : 0
    rescue StandardError
      0
    end

    # A string argument, or nil for NULL (nil or 0)
    def text(arg)
      arg.is_a?(String) ? arg.b.sub(/\0.*\z/m, '') : nil
    end
  end

  # A stand-in that does what Windows would and returns {value}
  def self.returning(value)
    Class.new { define_method(:call) { |_args| value } }
  end
end

module Win32API_Impl
  module User32
    class GetSystemMetrics
      def call(args)
        w, h = AgvnScreen.display
        case args[0]
        when 0, 16, 59, 61, 78 then w # the screen, a full-screen window, a maximized one, all screens
        when 1, 17, 60, 62, 79 then h
        when 80 then 1 # SM_CMONITORS
        else 0 # borders, title bar, menu bar (4, 5, 6, 7, 8, 32, 33, 92), and what else a script asks
        end
      end
    end

    class GetClientRect
      def call(args)
        w, h = args[0] == AgvnScreen::DESKTOP ? AgvnScreen.display : AgvnScreen.game
        AgvnScreen.rect(args[1], w, h)
      end
    end

    GetWindowRect = GetClientRect

    class GetMonitorInfo
      def call(args)
        w, h = AgvnScreen.display
        # MONITORINFO: cbSize, which the game set, rcMonitor, rcWork, dwFlags (MONITORINFOF_PRIMARY)
        AgvnScreen.fill(args[1], [0, 0, w, h, 0, 0, w, h, 1].pack('l9'), 4)
      end
    end

    class SystemParametersInfo
      def call(args)
        return 0 unless args[0] == 0x30 # SPI_GETWORKAREA
        AgvnScreen.rect(args[2], *AgvnScreen.display)
      end
    end

    class FindWindowA # win32_wrap.rb's, which knew the game's window by its class only
      def call(args)
        AgvnScreen.window(args[0], args[1])
      end
    end

    FindWindow = FindWindowA

    class FindWindowEx
      def call(args)
        AgvnScreen.window(args[2], args[3])
      end
    end

    # (window, index): a game window's styles, captioned and visible (WS_OVERLAPPED|CAPTION|SYSMENU|MINIMIZEBOX|
    # CLIPSIBLINGS|VISIBLE, WS_EX_WINDOWEDGE), as a full-screen script reads them before it changes them; 0 for the rest
    class GetWindowLong
      def call(args)
        { -16 => 0x14CA0000, -20 => 0x100 }.fetch(args[1].to_i, 0)
      end
    end
    GetWindowLongPtr = GetWindowLong

    MonitorFromWindow = MonitorFromPoint = MonitorFromRect = AgvnScreen.returning(AgvnScreen::MONITOR)
    GetDesktopWindow = AgvnScreen.returning(AgvnScreen::DESKTOP)
    GetForegroundWindow = GetActiveWindow = GetFocus = AgvnScreen.returning(AgvnScreen::GAME)
    GetDC = GetWindowDC = AgvnScreen.returning(AgvnScreen::DC)
    %i[MoveWindow SetWindowPos ShowWindow UpdateWindow SetWindowLong SetWindowLongPtr SetWindowText
       SetForegroundWindow BringWindowToTop AdjustWindowRect AdjustWindowRectEx ReleaseDC].each do |name|
      const_set(name, AgvnScreen.returning(1))
    end
  end

  module Gdi32
    class GetDeviceCaps
      def call(args)
        w, h = AgvnScreen.display
        case args[1]
        when 8, 118 then w # HORZRES, DESKTOPHORZRES
        when 10, 117 then h # VERTRES, DESKTOPVERTRES
        when 12 then 32 # BITSPIXEL
        when 88, 90 then 96 # LOGPIXELSX, LOGPIXELSY
        when 116 then 60 # VREFRESH
        else 0
        end
      end
    end

    CreateDC = AgvnScreen.returning(AgvnScreen::DC)
    DeleteDC = AgvnScreen.returning(1)
  end
end
