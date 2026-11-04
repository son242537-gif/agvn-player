# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_win32.rb, agvn_win32_screen.rb and agvn_win32_ini.rb over mkxp-z's
# win32_wrap.rb, on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-win32.rb
# Win32API here is mkxp-z's on a phone (MiniFFI, binding/miniffi-binding.cpp): no DLL loads, and Call is its native
# call under another name. Graphics, Input and System answer as mkxp-z on a 2400×1080 phone, an RGSS3 game at 544×416.
# A second run loads win32_wrap.rb alone and must stop as the report of 04/11/2026 did: NaN, then "wrong constant name".
require 'tmpdir'
require 'rbconfig'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
SELF_TEST = File.expand_path(__FILE__)
UPSTREAM = ENV['AGVN_TEST_UPSTREAM'] == '1'
TITLE = "Cô bé bán diêm"
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

def raised(what)
  yield
  nil
rescue StandardError => e
  puts "       #{what}: #{e.class}: #{e.message}"
  e
end

module Graphics
  def self.update; end
  def self.width = 544
  def self.height = 416
  def self.display_width = 1080 # SDL may give the display upright
  def self.display_height = 2400
end

module Input
  MOUSELEFT, MOUSERIGHT, MOUSEMIDDLE = 1, 2, 3
  def self.raw_key_states = [false] * 512
  def self.press?(_key) = false
  def self.mouse_x = 10
  def self.mouse_y = 20
end

module System
  def self.is_windows? = false
  def self.puts(_line); end
  def self.window_title = TITLE
end

class Win32API # mkxp-z's MiniFFI on a phone
  def initialize(dll, *_args)
    raise "dlopen failed: library \"#{dll}\" not found"
  end

  def call(*_args)
    raise 'MiniFFI call with no function: a native crash on the phone'
  end
  alias_method :Call, :call
end

# A full-screen script of the Fullscreen++ kind, as the report tells it: the ratio from what Windows measures
def window_ratio
  find = Win32API.new('user32', 'FindWindow', 'pp', 'i')
  metrics = Win32API.new('user32.dll', 'GetSystemMetrics', 'i', 'i')
  hwnd = find.call('RGSS Player', 0)
  client = [0, 0, 0, 0].pack('l4')
  frame = [0, 0, 0, 0].pack('l4')
  Win32API.new('user32', 'GetClientRect', 'lp', 'i').call(hwnd, client)
  Win32API.new('User32', 'GetWindowRect', 'lp', 'i').call(hwnd, frame)
  cw, ch = client.unpack('l4')[2, 2]
  l, t, r, b = frame.unpack('l4')
  border_w = r - l - cw + metrics.call(32) * 2
  border_h = b - t - ch + metrics.call(4)
  x = (metrics.call(0) - border_w).to_f / (r - l)
  y = (metrics.call(1) - border_h).to_f / (b - t)
  (x < y ? x : y).to_i
end

Dir.mktmpdir('agvn-win32') do |dir|
  log = File.join(dir, 'tim-tep.txt')
  File.write(log, "game=#{dir}\n")
  ENV['AGVN_RGSS_FILES_LOG'] = log
  Dir.chdir(dir)
  load File.join(ASSETS, 'win32_wrap.rb')
  if UPSTREAM
    e = raised('win32_wrap.rb alone') { window_ratio }
    check('win32_wrap.rb alone: NaN, as the report', e.is_a?(FloatDomainError) && e.message == 'NaN')
    e = raised('win32_wrap.rb alone') { Win32API.new('System/KGL2.klib', 'KGL2_Init', 'i', 'i') }
    check('win32_wrap.rb alone: wrong constant name', e.is_a?(NameError) && e.message.include?('System/KGL2.klib'))
    exit($failures.zero? ? 0 : 1)
  end
  %w[agvn_win32.rb agvn_win32_screen.rb agvn_win32_ini.rb].each { |f| load File.join(ASSETS, f) }

  check('a full-screen script gets a ratio, not NaN', raised('ratio') { $ratio = window_ratio }.nil? && $ratio == 2)
  {
    'System/KGL2.klib' => 'KGL2', 'user32.dll' => 'User32', 'USER32.DLL' => 'USER32', 'System\\Ex.Lib.dll' => 'Ex',
    '3d-fx.dll' => 'Dll_3d_fx', '' => 'Dll_', '.dll' => 'Dll__dll'
  }.each { |dll, name| check("#{dll.inspect} is named #{name}", AgvnWin32.const_name(dll) == name) }

  light = raised('light DLL') { $light = Win32API.new('System/KGL2.klib', 'KGL2_Init', 'i', 'i') }
  check('a DLL the game ships, named with a folder and its own extension, does not stop the game', light.nil?)
  check('its functions return 0, by call and by Call', $light.call(7) == 0 && $light.Call(7) == 0)
  Win32API.new('System/KGL2.klib', 'KGL2_Init', 'i', 'i').call(1)
  lines = File.binread(log).split("\n")
  check('one log line for it, after the game line', lines == ["game=#{dir}", 'Win32API giả: System/KGL2.klib!KGL2_Init']
    .map(&:b))
  check('a DLL with no name does not stop the game', raised('no name') { Win32API.new('', 'X').call } .nil?)

  %w[user32 User32 user32.dll USER32.DLL].each do |dll|
    m = Win32API.new(dll, 'GetSystemMetrics', 'i', 'i')
    check("#{dll}: the screen is the phone's, turned as the game", [m.call(0), m.call(1)] == [2400, 1080])
    check("#{dll}: work area too, no border or title bar", [m.call(16), m.call(17), m.call(4), m.call(32)] ==
      [2400, 1080, 0, 0])
  end
  [0, 42, 0x123].each do |hwnd|
    rect = [9, 9, 9, 9].pack('l4')
    ok = Win32API.new('user32', 'GetClientRect', 'lp', 'i').call(hwnd, rect) == 1
    check("GetClientRect of window #{hwnd}: the game's size", ok && rect.unpack('l4') == [0, 0, 544, 416])
  end
  desktop = Win32API.new('user32', 'GetDesktopWindow', '', 'l').call
  rect = [0, 0, 0, 0].pack('l4')
  Win32API.new('user32', 'GetWindowRect', 'lp', 'i').call(desktop, rect)
  check("GetWindowRect of the desktop: the phone's screen", rect.unpack('l4') == [0, 0, 2400, 1080])

  info = [40].pack('l') + "\0" * 36
  monitor = Win32API.new('user32', 'MonitorFromWindow', 'll', 'l').call(42, 2)
  ok = Win32API.new('user32', 'GetMonitorInfoA', 'lp', 'i').call(monitor, info) == 1
  check('GetMonitorInfo: cbSize kept, monitor and work area the screen', ok && monitor != 0 &&
    info.unpack('l10') == [40, 0, 0, 2400, 1080, 0, 0, 2400, 1080, 1])
  short = [40].pack('l') + "\0" * 4
  check('GetMonitorInfo with a short buffer writes what fits', Win32API.new('user32', 'GetMonitorInfo', 'lp', 'i')
    .call(monitor, short) == 1 && short.unpack('l2') == [40, 0])
  area = [0, 0, 0, 0].pack('l4')
  Win32API.new('user32', 'SystemParametersInfo', 'iipi', 'i').call(0x30, 0, area, 0)
  check('SystemParametersInfo: the work area is the screen', area.unpack('l4') == [0, 0, 2400, 1080])
  caps = Win32API.new('gdi32', 'GetDeviceCaps', 'ii', 'i')
  hdc = Win32API.new('gdi32', 'CreateDC', 'ppii', 'i').call('DISPLAY', nil, 0, 0)
  check('GetDeviceCaps: the screen, 96 dpi', [caps.call(hdc, 8), caps.call(hdc, 10), caps.call(hdc, 88)] ==
    [2400, 1080, 96])

  %w[MoveWindow SetWindowPos ShowWindow UpdateWindow SetWindowLong SetWindowLongA AdjustWindowRect].each do |f|
    check("#{f} succeeds and does nothing", Win32API.new('user32', f, 'l', 'i').call(42) == 1)
  end
  find = Win32API.new('user32', 'FindWindowA', 'pp', 'l')
  check('FindWindow by class finds the game', find.call('RGSS Player', 0) == 42)
  check("FindWindow by the game's title finds it", find.call(0, TITLE.b) == 42 && find.call(nil, TITLE) == 42)
  check("FindWindow finds no other program's window", find.call(nil, 'Cheat Engine 7.5') == 0 &&
    find.call('TMainForm', nil) == 0)
  check('FindWindowEx by class finds the game', Win32API.new('user32', 'FindWindowExA', 'llpp', 'l')
    .call(0, 0, 'RGSS Player', 0) == 42)

  File.binwrite('Game.ini', "\xEF\xBB\xBF[Game]\r\nTitle=#{TITLE}\r\n; a comment\r\n[fullscreen++]\r\n" \
    "WindowedRatio = 2 \r\nFullscreen=\"1\"\r\nName=abcdef\r\n".b)
  get = Win32API.new('kernel32', 'GetPrivateProfileStringA', 'ppppip', 'i')
  read = lambda do |section, key, default, file = './Game.ini', size = 256|
    buf = ("\0" * 256).b
    n = get.call(section, key, default, buf, size, file)
    buf.byteslice(0, n) if buf.getbyte(n) == 0
  end
  check('GetPrivateProfileString reads a key, section in any case',
    read.call('Fullscreen++', 'windowedratio', '1') == '2')
  check('a quoted value loses its quotes', read.call('Fullscreen++', 'Fullscreen', '0') == '1')
  check('a missing key, section or file gives the default', read.call('Fullscreen++', 'FullscreenRatio', '0') == '0' &&
    read.call('Other', 'Title', 'x') == 'x' && read.call('Game', 'Title', 'd', './None.ini') == 'd')
  check("a name without a folder is in Windows' folder: the default",
    read.call('Game', 'Title', 'd', 'Game.ini') == 'd')
  check('a value longer than the buffer is cut, with its 0',
    read.call('Fullscreen++', 'Name', '', './Game.ini', 3) == 'ab')
  check("the game's title, as its bytes", read.call('Game', 'Title', '', '.\\Game.ini') == TITLE.b)
  int = Win32API.new('Kernel32.dll', 'GetPrivateProfileInt', 'ppip', 'i')
  check('GetPrivateProfileInt: the number, the default when missing, 0 when not a number',
    [int.call('Fullscreen++', 'WindowedRatio', 9, './Game.ini'), int.call('Fullscreen++', 'X', 7, './Game.ini'),
     int.call('Fullscreen++', 'Name', 7, './Game.ini')] == [2, 7, 0])

  pos = [0, 0].pack('l2')
  check("win32_wrap.rb's own stand-ins still answer", Win32API.new('user32', 'GetCursorPos', 'p', 'i').call(pos) == 1 &&
    pos.unpack('l2') == [10, 20] && Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i').call(0x41) == 0)
  failing = Win32API.new('user32', 'GetKeyboardState', 'p', 'i')
  check('a stand-in that fails returns 0', raised('stand-in') { $got = failing.call("\0" * 16) }.nil? && $got == 0)
  check('and says so once', File.binread(log).scan('Win32API giả: user32!GetKeyboardState (IndexError'.b).size == 1)
  Graphics.singleton_class.send(:remove_method, :display_width)
  metrics = Win32API.new('user32', 'GetSystemMetrics', 'i', 'i')
  check("no display size from mkxp-z: the game's own", metrics.call(0) == 544)
  Dir.chdir(__dir__)
end

upstream = IO.popen({ 'AGVN_TEST_UPSTREAM' => '1' }, [RbConfig.ruby, SELF_TEST], err: %i[child out], &:read)
puts upstream
check('win32_wrap.rb alone stops as the report said', $?.success?)
puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero? ? 0 : 1)
