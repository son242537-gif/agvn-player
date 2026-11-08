# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks agvn_dl_cfunc.rb, agvn_win32_mouse.rb, agvn_win32_text.rb, agvn_ruby19.rb and the frozen Win32API of
# agvn_win32.rb, over mkxp-z's win32_wrap.rb, on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-dl-cfunc.rb
# The game scripts here are written for this test, in the shapes Rei and the Floating City's take (report of
# 08/11/2026): a resolution script that calls user32 and kernel32 through DL as it loads and picks its Plane code by
# File.basename of Game.ini's Library, a mouse script that asks for System/CAMouse in a rescue, a cursor that hides
# itself with ShowCursor in a loop, a status screen that changes nil.to_s.clone, and a frozen Win32API.
require 'tmpdir'
require 'fileutils'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

module Graphics
  def self.update; end
  def self.width = 544
  def self.height = 416
end

module Input
  MOUSELEFT, MOUSERIGHT, MOUSEMIDDLE = 101, 102, 103
  def self.update; end
  def self.raw_key_states = Array.new(512, false)
  def self.press?(_button) = false
  def self.mouse_x = 0
  def self.mouse_y = 0
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
  eval(File.read(path), TOPLEVEL_BINDING, path)
end

Dir.mktmpdir('agvn-cfunc') do |dir|
  Dir.chdir(dir)
  FileUtils.mkdir_p(%w[Data/Scripts System])
  File.binwrite('System/CAMouse.dll', 'MZ')
  File.binwrite('System/Own.dll', 'MZ')
  File.binwrite('Game.ini', "[Game]\r\nLibrary=System\\RGSS301.dll\r\nTitle=Rei\r\n")
  ENV['AGVN_RGSS_FILES_LOG'] = File.join(dir, 'tim-tep.txt')
  %w[agvn_ruby19.rb win32_wrap.rb agvn_win32.rb agvn_win32_screen.rb agvn_win32_ini.rb
     agvn_win32_text.rb agvn_win32_rescue.rb agvn_win32_input.rb agvn_win32_mouse.rb agvn_dl.rb
     agvn_dl_cfunc.rb].each { |f| load File.join(ASSETS, f) }

  run_script('Data/Scripts/142_Resolution.rb', <<~'RUBY')
    module Graphics
      User32 = DL.dlopen('user32')
      Kernel32 = DL.dlopen('kernel32')
      GetSystemMetrics = DL::CFunc.new(User32['GetSystemMetrics'], DL::TYPE_LONG)
      ScreenW = GetSystemMetrics.call([0])
      Ini = DL::CFunc.new(Kernel32['GetPrivateProfileStringA'], DL::TYPE_LONG)
      buf = DL::CPtr.malloc(256)
      Got = Ini.call([DL::CPtr['Game'].to_i, DL::CPtr['Library'].to_i, DL::CPtr[''].to_i, buf.to_i, 256,
                      DL::CPtr['.\Game.ini'].to_i])
      Lib = buf.to_s
      Unknown = DL::CFunc.new(User32['NoSuchFunction'], DL::TYPE_LONG).call([1, 2])
      PlaneSpeedUp = true if File.basename(Lib) == 'RGSS301.dll'
    end
  RUBY
  check('DL.dlopen("user32") gives a handle, and a function of it is called', Graphics::ScreenW == 544)
  check('a C string from DL::CPtr[] reaches GetPrivateProfileString', Graphics::Got == 18 && Graphics::Lib == 'System\RGSS301.dll')
  check('a function nothing stands in for returns 0', Graphics::Unknown.zero?)
  check('File.basename reads "\" as Windows does: the script defines PlaneSpeedUp', Graphics.const_defined?(:PlaneSpeedUp))
  check('dirname, extname and split too', File.dirname('System\RGSS301.dll') == 'System' &&
        File.extname('System\RGSS301.dll') == '.dll' && File.split('a\b.txt') == %w[a b.txt])

  run_script('Data/Scripts/145_Mouse.rb', <<~'RUBY')
    module InputMouse
      begin
        GetWindowHandle = Win32API.new('System/CAMouse', 'GetWindowHandle', '', 'l')
        WindowHandle = GetWindowHandle.call
        GetWheelDelta = Win32API.new('System/CAMouse', 'GetWheelDelta', '', 'l')
      rescue
        Fallback = true
      end
      def self.own_dll
        DL.dlopen('System/Own.dll')
        :loaded
      rescue DL::DLError
        :raised
      end
    end
  RUBY
  check('CAMouse asked for in a rescue: the game window 42, no fallback', InputMouse::WindowHandle == 42 &&
        !InputMouse.const_defined?(:Fallback) && InputMouse::GetWheelDelta.call.zero?)
  check('a DLL of the game opened with DL in a rescue raises, as Win32API.new does', InputMouse.own_dll == :raised)
  check('...outside a rescue it gives a handle', DL.dlopen('System/Own.dll').is_a?(DL::Handle))

  show = Win32API.new('user32', 'ShowCursor', 'i', 'i')
  calls = 0
  calls += 1 while show.call(0) >= 0 && calls < 100
  check('ShowCursor(0) in a loop until it is below 0 ends at once', calls.zero? && show.call(1) == 0)

  text = nil.to_s.clone
  text.gsub!(/\A/, 'Lv ')
  flag = true.to_s.clone
  flag << '!'
  check('nil.to_s.clone and true.to_s.clone can be changed', text == 'Lv ' && flag == 'true!' && false.to_s == 'false')

  frozen = Win32API.new('user32', 'NoSuchThing', '', 'i').freeze
  ok = begin
    frozen.call.zero? && frozen.call.zero?
  rescue FrozenError
    false
  end
  check('a frozen Win32API with no stand-in returns 0, twice', ok)

  m2w = Win32API.new('kernel32', 'MultiByteToWideChar', 'iipipi', 'i')
  sjis = "日本\0".encode('Windows-31J').b
  wide = "\0".b * 6
  check('MultiByteToWideChar: the size, then the text, from Shift_JIS',
        m2w.call(932, 0, sjis, -1, nil, 0) == 3 && m2w.call(932, 0, sjis, -1, wide, 3) == 3 &&
        wide.force_encoding('UTF-16LE') == "日本\0".encode('UTF-16LE'))
  w2m = Win32API.new('kernel32', 'WideCharToMultiByte', 'iipipipp', 'i')
  utf8 = "\0".b * 8
  check('WideCharToMultiByte to UTF-8', w2m.call(65001, 0, "ắ\0".encode('UTF-16LE').b, -1, nil, 0, nil, nil) == 4 &&
        w2m.call(65001, 0, "ắ\0".encode('UTF-16LE').b, -1, utf8, 8, nil, nil) == 4 && utf8.byteslice(0, 3) == 'ắ'.b)
  check('GetWindowLong gives a captioned window style',
        Win32API.new('user32', 'GetWindowLong', 'ii', 'i').call(42, -16) == 0x14CA0000)

  buffer = DL::CPtr.new(DL.malloc(8), 8)
  buffer[1] = 0x80
  check("DL.malloc and DL::CPtr as Lona RPG uses them still work", DL::CPtr.new(buffer.to_i, 8)[1] == -128)
end

puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero? ? 0 : 1)
