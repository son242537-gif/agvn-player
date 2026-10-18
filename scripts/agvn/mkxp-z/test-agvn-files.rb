# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_files.rb and agvn_names.rb on a PC with Ruby 3.1 or newer (mkxp-z has
# Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-files.rb
# Audio, Bitmap and Graphics here look files up as mkxp-z does (src/filesystem/filesystem.cpp, FileSystem::openRead
# with its path cache): A-Z lowered, every other byte compared as it is, a name without extension matching
# "<name>.<any extension>", and a file not found raised as Errno::ENOENT with the name as bytes (raiseRbExc).
# A second run copies the script where Ruby's unicode_normalize is not, as on a phone without it: names that differ
# only in case are still found, and the game still starts. Names are written with \u escapes, so this file never
# loads unicode_normalize itself.
require 'tmpdir'
require 'fileutils'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
SELF_TEST = File.expand_path(__FILE__)
NO_LIB = ENV['AGVN_TEST_NO_LIB'] == '1'
# The game's names (NFC, as Windows keeps them) and the phone's (NFD, accents and kana marks apart)
GUIDE, GUIDE_NFD = "bgm\u30AC\u30A4\u30C9\u30D6\u30C3\u30AF", "bgm\u30AB\u3099\u30A4\u30C8\u3099\u30D5\u3099\u30C3\u30AF"
PHOTO, PHOTO_NFD = "\u1EA2nh n\u1EC1n", "A\u0309nh ne\u0302\u0300n"
STAND, STAND_NFD = "\u7ACB\u3061\u7D75\u30AC", "\u7ACB\u3061\u7D75\u30AB\u3099"
GA, GA_NFD = "\u30AC", "\u30AB\u3099"
WIPE, WIPE_NFD = "\u30EF\u30A4\u30D7", "\u30EF\u30A4\u30D5\u309A"
OPENING, OPENING_NFD = "\u30AA\u30FC\u30D7\u30CB\u30F3\u30B0", "\u30AA\u30FC\u30D5\u309A\u30CB\u30F3\u30AF\u3099"
TEST = "bgm\u30C6\u30B9\u30C8"
# Names an unpacker read with the wrong code page, as tim-tep.txt of Rj245859 (nubia Z2465N, 18/10/2026) showed them,
# and the game's names they were: UTF-8 read as GB18030, the last byte of a letter lost ("\uFFFD") or not
FIELD, FIELD_GB = "bgm\u30D5\u30A3\u30FC\u30EB\u30C93", "bgm\u9289\u66D8\u5045\u9289\u7B3A\u5137\u9289\uFFFD3"
CUE, CUE_GB = "a\u6C4E\u7528\u30AD\u30E5\u30D4\u30FC\u30F3", "a\u59F9\u5EA3\u6564\u9288\uE15C\u5131\u9289\u65BB\u5157\u9289\uFFFD"
OPEN_GB = "bgm\u9288\uE042\u5157\u9289\u693C\u5116\u9289\u70BD\u5058" # bgm + OPENING, no byte lost
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

def opens(what, expect)
  got = begin
    yield
  rescue StandardError => e
    e
  end
  same = got.is_a?(String) && expect.is_a?(String) ? got.b == expect.b : got == expect
  check(what, same)
  puts "       got #{got.inspect}" unless same
end

module FakeMkxp
  def self.build(root)
    @lists = Hash.new { |h, k| h[k] = [] }
    Dir.glob('**/*', base: root).each do |rel|
      next unless File.file?(File.join(root, rel))
      dir, _, file = rel.b.rpartition('/')
      @lists[dir.tr('A-Z', 'a-z')] << [file.tr('A-Z', 'a-z'), rel.b]
    end
  end

  def self.open(name)
    req = name.b.tr('\\', '/').sub(%r{\A(\./)+}, '').tr('A-Z', 'a-z')
    dir, _, file = req.rpartition('/')
    hit = @lists[dir].find { |n, _| n.start_with?(file) && ['.', nil].include?(n[file.size]) }
    raise Errno::ENOENT, name.b unless hit
    raise IOError, 'not an image' if hit[1].end_with?('.bad')
    hit[1]
  end
end

module Audio
  class << self
    %i[bgm_play bgs_play me_play se_play].each do |m|
      define_method(m) { |name, _volume = 100, _pitch = 100, *_rest| FakeMkxp.open(name) }
    end
  end
end

class Bitmap
  attr_reader :path

  def initialize(*args)
    @path = args.size == 1 ? FakeMkxp.open(args[0]) : args
  end
end

module Graphics
  def self.transition(duration = 8, filename = '', _vague = 40)
    filename.empty? ? duration : FakeMkxp.open(filename)
  end

  def self.play_movie(name, _volume = 100, _skip = false)
    FakeMkxp.open(name)
  end
end

Dir.mktmpdir('agvn-files') do |tmp|
  game = File.join(tmp, 'game')
  files = ["Audio/BGM/#{GUIDE_NFD}.ogg", 'Audio/BGM/theme1.ogg', "Audio/BGM/#{TEST}2.ogg", "Audio/SE/\u6CE2\u301C\u97F3.ogg",
           "Graphics/Pictures/\uFF41\uFF42\uFF43.png", "Graphics/Pictures/#{PHOTO_NFD}.png", "Graphics/#{STAND_NFD}/#{GA_NFD}.png",
           'Graphics/Pictures/broken.bad', "Graphics/Transitions/#{WIPE_NFD}.png", "Movies/#{OPENING_NFD}.ogv",
           "Audio/BGM/\x83\x4B.ogg".b, # a name that is not UTF-8 (Shift_JIS bytes kept as they were)
           "Audio/BGM/#{FIELD_GB}.ogg", "Audio/SE/#{CUE_GB}.ogg", "Audio/BGM/#{OPEN_GB}.ogg",
           "Graphics/Pictures/\u00E3\u201A\u00AC.png", # UTF-8 "\u30AC" read as Windows-1252
           "Audio/ME/w\uFFFD.ogg", "Audio/ME/w\uFFFD\uFFFD.ogg"] # two names a lost byte leaves open: neither is taken
  files.each { |f| FileUtils.mkdir_p(File.join(game, File.dirname(f))); File.write(File.join(game, f), '') }
  FakeMkxp.build(game)
  late = File.join(game, "Audio/ME/#{GA_NFD}.ogg") # on the phone, not in mkxp-z's list (made after the game started)
  FileUtils.mkdir_p(File.dirname(late))
  File.write(late, '')
  scripts = %w[agvn_names.rb agvn_files.rb].map { |f| File.join(NO_LIB ? tmp : ASSETS, f) }
  scripts.each { |f| FileUtils.cp(File.join(ASSETS, File.basename(f)), f) } if NO_LIB
  script = scripts.last
  log = File.join(tmp, 'tim-tep.txt')
  ENV['AGVN_RGSS_FILES_LOG'] = log
  Dir.chdir(game) do
    # mkxp-z links every code page into its Ruby (--with-static-linked-ext); a PC's Ruby loads them from its library
    %w[GB18030 Windows-31J Big5 CP949 IBM437 Windows-1252 ISO-8859-1].each { |p| "\u00E9".encode(p, undef: :replace) }
    $LOAD_PATH.clear # mkxp-z starts Ruby with only the game's folder there, and no library
    scripts.each { |f| eval(File.read(f, encoding: 'UTF-8'), nil, f) } # as mkxp-z runs its preload scripts

    opens('exact name, case aside (mkxp-z finds it)', 'Audio/BGM/theme1.ogg') { Audio.bgm_play('Audio/BGM/Theme1', 90) }
    if NO_LIB
      e = (Audio.bgm_play("Audio/BGM/#{GUIDE}") rescue $!)
      check('NFD on the phone, without unicode_normalize: the game\'s own error', e.is_a?(Errno::ENOENT))
    else
      opens('NFD on the phone, NFC in the game', "Audio/BGM/#{GUIDE_NFD}.ogg") { Audio.bgm_play("Audio/BGM/#{GUIDE}") }
      opens('the same again, from what was found', "Audio/BGM/#{GUIDE_NFD}.ogg") { Audio.bgm_play("Audio/BGM/#{GUIDE}", 80) }
      opens('Vietnamese NFD, a picture', "Graphics/Pictures/#{PHOTO_NFD}.png") { Bitmap.new("Graphics/Pictures/#{PHOTO}").path }
      opens('folder and file NFD, backslashes', "Graphics/#{STAND_NFD}/#{GA_NFD}.png") { Bitmap.new("Graphics\\#{STAND}\\#{GA}").path }
      opens('transition picture', "Graphics/Transitions/#{WIPE_NFD}.png") { Graphics.transition(20, "Graphics/Transitions/#{WIPE}") }
      opens('movie', "Movies/#{OPENING_NFD}.ogv") { Graphics.play_movie("Movies/#{OPENING}") }
    end
    opens('full-width letters in other case', "Graphics/Pictures/\uFF41\uFF42\uFF43.png") { Bitmap.new("Graphics/Pictures/\uFF21\uFF22\uFF23").path }
    opens('WAVE DASH on the phone, FULLWIDTH TILDE in the game', "Audio/SE/\u6CE2\u301C\u97F3.ogg") { Audio.se_play("Audio/SE/\u6CE2\uFF5E\u97F3", 80) }
    opens('UTF-8 read as GB18030, a byte lost', "Audio/BGM/#{FIELD_GB}.ogg") { Audio.bgm_play("Audio/BGM/#{FIELD}") }
    opens('  the same, with a private-use letter', "Audio/SE/#{CUE_GB}.ogg") { Audio.se_play("Audio/SE/#{CUE}") }
    opens('  no byte lost', "Audio/BGM/#{OPEN_GB}.ogg") { Audio.bgm_play("Audio/BGM/bgm#{OPENING}") }
    opens('UTF-8 read as Windows-1252', "Graphics/Pictures/\u00E3\u201A\u00AC.png") { Bitmap.new("Graphics/Pictures/#{GA}").path }
    opens('Shift_JIS bytes kept as they were', "Audio/BGM/\x83\x4B.ogg") { Audio.bgm_play("Audio/BGM/#{GA}") }
    e = (Audio.me_play("Audio/ME/w\u00E9") rescue $!)
    check('two names a lost byte leaves open: the game\'s own error', e.is_a?(Errno::ENOENT))
    opens('a bitmap by size', [32, 16]) { Bitmap.new(32, 16).path }
    opens('a transition without picture', 10) { Graphics.transition(10) }

    2.times do |i|
      e = (Audio.bgm_play("Audio/BGM/#{TEST}", 100, 100) rescue $!)
      check("a file the game lacks: Errno::ENOENT (#{i + 1})", e.is_a?(Errno::ENOENT))
      check('  its message is UTF-8 and readable', e.message.encoding == Encoding::UTF_8 && e.message.end_with?(TEST))
      check('  no line of agvn_files.rb in its backtrace', e.backtrace.none? { |l| l.start_with?(script) })
      check('  the backtrace starts at mkxp-z or the game', e.backtrace.first.start_with?(__FILE__))
    end
    2.times do |i|
      e = (Audio.me_play("Audio/ME/#{GA}") rescue $!)
      check("found on the phone, still not by mkxp-z: the game's own error (#{i + 1})", e.is_a?(Errno::ENOENT) && e.message.end_with?(GA))
    end
    e = (Bitmap.new('Graphics/Pictures/broken') rescue $!)
    check('another error passes as it was, without agvn_files.rb lines', e.is_a?(IOError) && e.backtrace.none? { |l| l.start_with?(script) })
  end

  text = File.read(log, encoding: 'UTF-8')
  puts text.gsub(/^/, '       | ')
  check('the log tells each name once', text.lines.size == (NO_LIB ? 11 : 16))
  check('the log says how a name was garbled', text.include?("UTF-8 \u0111\u1ECDc th\u00E0nh GB18030") &&
        text.include?("UTF-8 \u0111\u1ECDc th\u00E0nh Windows-1252") && text.include?("Windows-31J gi\u1EEF nguy\u00EAn byte"))
  check('the log names what the game lacks and what is near it', text.include?("Kh\u00F4ng c\u00F3: game h\u1ECFi \"Audio/BGM/#{TEST}\"") && text.include?("#{TEST}2.ogg"))
  check('the log shows code points', NO_LIB || text.include?('<30AB 3099 30A4 30C8 3099'))
end

unless NO_LIB
  puts '-- without Ruby\'s unicode_normalize --'
  $failures += 1 unless system({ 'AGVN_TEST_NO_LIB' => '1' }, RbConfig.ruby, SELF_TEST)
end
puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero?)
