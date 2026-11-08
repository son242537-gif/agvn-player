# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_win32_input.rb, before the game's
# scripts.
# - CAMouse (System/CAMouse.dll), the mouse DLL "ユニバーサル・マウス" and "エセフルスクリーン" ship: it hooks the game's
#   window and gives its handle and the wheel. The script asks for it inside a rescue, so the phone's missing DLL
#   raised (agvn_win32_rescue.rb) and InputMouse::WindowHandle was never set; the next script read it and the game
#   stopped with a NameError (Rei and the Floating City, 08/11/2026). Here it gives the game's window (AgvnScreen's 42,
#   as FindWindow does) and no wheel turn; its hooks have nothing to do: mkxp-z reads the touches.
# - ShowCursor returns the cursor's count, as Windows does. win32_wrap.rb's returned whether the cursor shows, a 1 or a
#   0, so "ShowCursor(0) while ShowCursor(0) >= 0", the way a script hides the cursor, never ended.
module Win32API_Impl
  module CAMouse
    class GetWindowHandle
      def call(_args)
        defined?(AgvnScreen) ? AgvnScreen::GAME : 42
      end
    end

    GetWheelDelta = Class.new { def call(_args) = 0 }
    DisposeHook = Class.new { def call(_args) = 0 }
    RegenerationHook = Class.new { def call(_args) = 0 }
  end

  module User32
    class ShowCursor
      def initialize
        @cursor_count = 0
      end

      def call(args)
        show = args[0]
        show = show != 0 if show.is_a?(Integer)
        @cursor_count += show ? 1 : -1
        Graphics.show_cursor = @cursor_count >= 0 if Graphics.respond_to?(:show_cursor=)
        @cursor_count
      end
    end
  end
end
