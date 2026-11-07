# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_text_files.rb on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-text-files.rb
# Encodings are set as mkxp-z sets them (binding-mri.cpp: UTF-8 inside and outside). A second run without the script
# must stop as Lona RPG did on the phone (log of 04/11/2026): "stack level too deep" in a reader that skips spaces, "\n"
# and "\t" only, and reads the same "[" again at any other character, as its JSON reader does. And its error log, which
# prints a string of bytes holding UTF-8 text into a file, must write it, where without the script it stopped on the
# phone with '"\xE2" from ASCII-8BIT to UTF-8' (07/11/2026).
require 'tmpdir'
require 'rbconfig'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
SELF_TEST = File.expand_path(__FILE__)
UPSTREAM = ENV['AGVN_TEST_UPSTREAM'] == '1'
Encoding.default_internal = Encoding::UTF_8
Encoding.default_external = Encoding::UTF_8
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

# A JSON-like reader with Lona RPG's flaw, written for this test: arrays of words only
module FlawedReader
  def self.decode(text)
    @text = text
    @at = 0
    value
  end

  def self.token
    c = @text[@at, 1]
    @at += 1
    return token if [' ', "\n", "\t"].include?(c)
    return c if ['[', ']', ','].include?(c)
    @at -= 1
    c.nil? || c.empty? ? nil : :word
  end

  def self.value
    t = token
    return array if t == '['
    word if t == :word
  end

  def self.array
    out = []
    loop do
      t = token
      break if t == ']' || t.nil?
      next if t == ','
      @at -= 1 # anything else starts a value: read it again from there
      out << value
    end
    out
  end

  def self.word
    w = @text[@at..][/\A\w+/] || ''
    @at += [w.size, 1].max
    w
  end
end

Dir.mktmpdir('agvn-text') do |dir|
  json = File.join(dir, 'Lona.json')
  File.binwrite(json, "[\r\n\t[ red, green ],\r\n\t[ blue ]\r\n]\r\n")
  if UPSTREAM
    e = begin
      FlawedReader.decode(File.open(json, 'r', &:read))
      nil
    rescue SystemStackError => error
      error
    end
    check('without agvn_text_files.rb: stack level too deep, as on the phone', e.is_a?(SystemStackError))
    w = (File.open(File.join(dir, 'ErrorLog.txt'), 'w') { |f| f.print("Lona\xE2\x80\x99s error".b) } rescue $!)
    check("without agvn_text_files.rb: the error log stops, as on the phone (#{w})",
          w.is_a?(Encoding::UndefinedConversionError) && w.message.include?('\xE2'))
    exit($failures.zero? ? 0 : 1)
  end
  load File.join(ASSETS, 'agvn_text_files.rb')
  want = [%w[red green], %w[blue]]
  check('the JSON reads as on Windows', FlawedReader.decode(File.open(json, 'r', &:read)) == want)
  lf = "[\n\t[ red, green ],\n\t[ blue ]\n]\n"
  {
    'File.open(name)' => -> { File.open(json, &:read) },
    'File.open(name, "r:UTF-8")' => -> { File.open(json, 'r:UTF-8', &:read) },
    'File.open(name, encoding:)' => -> { File.open(json, encoding: 'UTF-8', &:read) },
    'File.open(name, mode: "r")' => -> { File.open(json, mode: 'r', &:read) },
    'File.new(name).read' => -> { f = File.new(json); s = f.read; f.close; s },
    'open(name).read' => -> { f = open(json); s = f.read; f.close; s },
    'File.read(name)' => -> { File.read(json) },
    'File.read(name, encoding:)' => -> { File.read(json, encoding: 'UTF-8') },
    'IO.readlines(name).join' => -> { IO.readlines(json).join },
    'File.foreach(name).to_a.join' => -> { File.foreach(json).to_a.join },
    'File.open(name) { each_line }' => -> { File.open(json) { |f| f.each_line.to_a.join } }
  }.each { |what, read| check("#{what}: \"\\r\\n\" reads as \"\\n\"", read.call == lf) }
  lines = File.readlines(json, chomp: true)
  check('File.readlines(name, chomp: true) has no "\r"', lines.none? { |l| l.include?("\r") })
  crlf = File.binread(json)
  {
    'File.open(name, "rb")' => -> { File.open(json, 'rb', &:read) },
    'File.binread(name)' => -> { File.binread(json) },
    'File.read(name, mode: "rb")' => -> { File.read(json, mode: 'rb') },
    'File.open(name, File::RDONLY)' => -> { File.open(json, File::RDONLY, &:read) },
    'File.open(name, "r", binmode: true)' => -> { File.open(json, 'r', binmode: true, &:read) },
    'File.open(name, "r+")' => -> { File.open(json, 'r+', &:read) }
  }.each { |what, read| check("#{what}: kept as the file has it", read.call.b == crlf) }
  out = File.join(dir, 'save.txt')
  File.open(out, 'w') { |f| f.write("a\nb\n") }
  check('writing writes what the game wrote', File.binread(out) == "a\nb\n")
  bytes = "c\xFF\x82\xA0\r\n".b
  File.binwrite(out, bytes)
  check('bytes that are not UTF-8 still read', File.read(out).b == "c\xFF\x82\xA0\n".b)
  log = File.join(dir, 'ErrorLog.txt')
  msg = "Lona\xE2\x80\x99s error\n".b
  File.open(log, 'w') { |f| f.print(msg) } # Han_Nama_ErrorLog's own way
  check('a string of bytes holding UTF-8 is written as those bytes', File.binread(log) == msg)
  File.open(log, 'w') { |f| f.puts('Backtrace:', "\xE2\x80\xA6".b, 'end') }
  check('puts with several, one of them bytes', File.binread(log) == "Backtrace:\n\xE2\x80\xA6\nend\n".b)
  File.open(log, 'w') { |f| f << 'a' << "\xFF\xFE".b << "Tiếng Việt" }
  check('<<, bytes that are not UTF-8 too', File.binread(log) == "a\xFF\xFETiếng Việt".b)
  File.open(log, 'wb') { |f| f.write("\x00\xE2".b) }
  check('a binary file as before', File.binread(log) == "\x00\xE2".b)
  File.open(log, 'wb') { |f| Marshal.dump(["Lona\xE2\x80\x99s".b, 'Lona'], f) }
  check('Marshal.dump into a file', File.open(log, 'rb') { |f| Marshal.load(f) } == ["Lona\xE2\x80\x99s".b, 'Lona'])
  sjis = (File.open(log, 'w:Shift_JIS') { |f| f.write('★…') } rescue $!)
  check('a file the game opened in its own encoding: as before', !sjis.is_a?(Exception) || sjis.is_a?(EncodingError))
  missing = (File.open(File.join(dir, 'none.json')) rescue $!)
  check('a missing file: the game\'s own error', missing.is_a?(Errno::ENOENT))
  both = (File.open(json, 'rt', binmode: true) rescue $!)
  check('its own wrong arguments: its own error', both.is_a?(ArgumentError))
  runs = 0
  begin
    File.open(json) { runs += 1; raise ArgumentError, 'the game\'s' }
  rescue ArgumentError
    nil
  end
  check("an error of the game's block runs it once", runs == 1)
  runs = 0
  begin
    File.foreach(json) { runs += 1; raise ArgumentError, 'the game\'s' }
  rescue ArgumentError
    nil
  end
  check("an error of the game's foreach block runs it once", runs == 1)
end

upstream = IO.popen({ 'AGVN_TEST_UPSTREAM' => '1' }, [RbConfig.ruby, SELF_TEST], err: %i[child out], &:read)
puts upstream
check('without agvn_text_files.rb the reader stops as on the phone', $?.success?)
puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero? ? 0 : 1)
