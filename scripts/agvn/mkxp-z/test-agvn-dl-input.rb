# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_win32_rescue.rb, agvn_dl.rb and agvn_win32_input.rb over win32_wrap.rb
# and agvn_win32.rb, on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-dl-input.rb
# The game scripts here are written for this test, in the shapes Lona RPG's take (log of 04/11/2026): a palette changer
# that tries its own DLL in a rescue, a light library that asks for its own outside any, and a keyboard input that
# reads every key with GetKeyboardState into DL memory and puts its own Input.update in place of mkxp-z's.
require 'tmpdir'
require 'fileutils'
require 'rbconfig'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
SELF_TEST = File.expand_path(__FILE__)
PLAIN = ENV['AGVN_TEST_PLAIN'] == '1'
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

module Graphics
  @count = 0
  def self.update = @count += 1
  def self.frame_count = @count
  def self.width = 544
  def self.height = 416
end

module Input # mkxp-z's, with key A (SDL scancode 4) and the left mouse button down
  MOUSELEFT, MOUSERIGHT, MOUSEMIDDLE = 101, 102, 103
  @updates = 0
  class << self
    attr_accessor :updates
    def update = @updates += 1
    def raw_key_states = Array.new(512) { |i| i == 4 }
    def press?(button) = button == MOUSELEFT
    def mouse_x = 10
    def mouse_y = 20
  end
end

module System
  def self.is_windows? = false
  def self.puts(_line); end
end

class Win32API # mkxp-z's MiniFFI on a phone: no DLL loads
  def initialize(dll, *_args)
    raise "dlopen failed: library \"#{dll}\" not found"
  end

  def call(*_args)
    raise 'MiniFFI call with no function'
  end
end

def run_script(path, code)
  File.write(path, code)
  eval(File.read(path), TOPLEVEL_BINDING, path) # as Lona RPG's script loader does
end

if PLAIN # a game that updates Input itself, and reads keys through Win32API after it
  load File.join(ASSETS, 'win32_wrap.rb')
  %w[agvn_win32.rb agvn_win32_input.rb].each { |f| load File.join(ASSETS, f) }
  keys = Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i')
  Input.updates = 0
  5.times { Graphics.update; Input.update; keys.call(0x41) }
  check("a game that updates Input itself: mkxp-z's update never twice in a frame", Input.updates == 5)
  exit($failures.zero? ? 0 : 1)
end

Dir.mktmpdir('agvn-dl') do |dir|
  Dir.chdir(dir)
  FileUtils.mkdir_p(%w[Data/Scripts Data/PaletteChanger System])
  File.binwrite('Data/PaletteChanger/Optimization.dll', 'MZ')
  File.binwrite('System/Light.klib', 'MZ')
  ENV['AGVN_RGSS_FILES_LOG'] = File.join(dir, 'tim-tep.txt')
  %w[win32_wrap.rb agvn_win32.rb agvn_win32_screen.rb agvn_win32_ini.rb agvn_win32_rescue.rb agvn_win32_input.rb
     agvn_dl.rb].each { |f| load File.join(ASSETS, f) }

  run_script('Data/Scripts/1_Palette.rb', <<~RUBY)
    module Palette
      def self.load_library
        begin
          @add = Win32API.new("Data/PaletteChanger/Optimization.dll", "add_setting", "V", "I")
          @use_library = true
        rescue => ex
          @use_library = false
        end
      end
      def self.valid? = @use_library
      def self.modifier = (Win32API.new("Data/PaletteChanger/Optimization.dll", "x", "V", "I") rescue :none)
      def self.wrong_class
        Win32API.new("Data/PaletteChanger/Optimization.dll", "y", "V", "I")
        :stand_in
      rescue LoadError
        :load_error
      end
      def self.system_dll
        begin
          Win32API.new("user32", "NoSuchFunction", "V", "I")
          :stand_in
        rescue
          :raised
        end
      end
    end
    Palette.load_library
  RUBY
  check('a DLL of the game, asked for in a rescue: the script does without it', Palette.valid? == false)
  check('the same with a rescue modifier', Palette.modifier == :none)
  check('a rescue that does not take a RuntimeError gets the stand-in', Palette.wrong_class == :stand_in)
  check("a function of Windows' own DLLs gets the stand-in in a rescue", Palette.system_dll == :stand_in)
  log = File.binread(ENV['AGVN_RGSS_FILES_LOG'])
  check('the log says so', log.include?('Optimization.dll!add_setting (không nạp'.b))

  run_script('Data/Scripts/2_Light.rb', <<~RUBY)
    module Light
      def self.function(name) = Win32API.new("System/Light.klib", name, "", "i")
      LOAD = Light.function("load")
    end
  RUBY
  check('a DLL of the game asked for outside a rescue: the stand-in, 0', Light::LOAD.call == 0)
  $RGSS_SCRIPTS = [[1, 'Main', '', ''], [2, 'Palette', '', "begin\n  X = Win32API.new('System/Light.klib', 'a')\n" \
    "rescue => e\n  X = :fallback\nend\n"]]
  eval($RGSS_SCRIPTS[1][3], TOPLEVEL_BINDING, '001:Palette')
  check('a script of Scripts.rvdata2 is read too', X == :fallback)

  buffer = DL::CPtr.new(DL.malloc(8), 8)
  buffer[1] = 0x80
  buffer[2, 3] = 'abc'
  check('DL memory: a byte reads as a char, a string as written', buffer[1] == -128 && buffer[2, 3] == 'abc')
  check('DL memory: its address finds it again', DL::CPtr.new(buffer.to_i, 8)[1] == -128)

  run_script('Data/Scripts/3_Keys.rb', <<~RUBY)
    module Input
      GetKeyboardState = Win32API.new("user32.dll", "GetKeyboardState", "I", "I")
      @state = DL::CPtr.new(DL.malloc(256), 256)
      def self.update
        GetKeyboardState.call(@state.to_i)
        @down = (0..255).select { |k| @state[k] & 0x80 == 0x80 }
      end
      def self.down = @down
    end
  RUBY
  Input.updates = 0
  3.times { Graphics.update; Input.update }
  check('keys reach a script that reads them with GetKeyboardState into DL memory', Input.down == [0x01, 0x41])
  check("mkxp-z's own update runs once a frame for it", Input.updates.between?(2, 3))
end

plain = IO.popen({ 'AGVN_TEST_PLAIN' => '1' }, [RbConfig.ruby, SELF_TEST], err: %i[child out], &:read)
puts plain
check('(that check ran)', $?.success?)
puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero? ? 0 : 1)
