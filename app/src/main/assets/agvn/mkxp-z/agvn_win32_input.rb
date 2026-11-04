# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after win32_wrap.rb, before the game's scripts.
# Keys and the mouse read through Win32API (GetKeyboardState, GetAsyncKeyState, GetKeyState, GetCursorPos) are
# mkxp-z's: win32_wrap.rb's stand-ins read Input.raw_key_states and Input.mouse_x/y, which mkxp-z refreshes in its own
# Input.update, and the mouse buttons with Input.press?.
# A script that puts its own input in their place (Cidiomar's keyboard input, in Lona RPG) runs neither: on Windows,
# GetKeyboardState reads the keyboard itself. So mkxp-z's own update and press? stay at hand under other names:
# - when mkxp-z's update has not run for two frames, the first stand-in of a frame that reads keys runs it once. A
#   game that updates Input itself is never updated twice in a frame;
# - the mouse buttons are read with mkxp-z's own press?, not the one a script put in its place.
module AgvnInput
  @frame = 0
  @game_ran = -2
  @we_ran = -1

  class << self
    def frame!
      @frame += 1
    end

    def game_ran!
      @game_ran = @frame
    end

    # Before a stand-in reads keys or the mouse: mkxp-z's update, if nothing ran it this frame or the last
    def fresh
      return if @frame - @game_ran < 2 || @we_ran == @frame
      @we_ran = @frame
      Input.agvn_native_update
    rescue StandardError
      nil
    end
  end
end

begin
  class << Input
    alias_method :agvn_native_update, :update
    alias_method :agvn_native_press?, :press?

    def update(*args)
      AgvnInput.game_ran!
      agvn_native_update(*args)
    end
  end

  class << Graphics
    alias_method :agvn_input_graphics_update, :update

    def update(*args)
      agvn_input_graphics_update(*args)
      AgvnInput.frame!
    end
  end

  # win32_wrap.rb's, which cached the keys for the frame; the first reading of a frame comes after mkxp-z's update
  def get_raw_keystates
    if $win32KeyStates.nil?
      AgvnInput.fresh
      $win32KeyStates = Input.raw_key_states
    end
    $win32KeyStates
  end

  alias agvn_input_common_keystate common_keystate

  def common_keystate(vkey)
    button = { LBUTTON: :MOUSELEFT, RBUTTON: :MOUSERIGHT, MBUTTON: :MOUSEMIDDLE }[Scancodes::WIN32INV[vkey]]
    return agvn_input_common_keystate(vkey) unless button
    AgvnInput.fresh
    Input.agvn_native_press?(Input.const_get(button)) ? 1 : 0
  end

  module Win32API_Impl
    module User32
      class GetCursorPos
        alias_method :agvn_input_call, :call

        def call(args)
          AgvnInput.fresh
          agvn_input_call(args)
        end
      end
    end
  end
rescue StandardError
  nil # an mkxp-z without these reads keys as before
end
