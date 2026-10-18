# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before agvn_files.rb, which uses it.
# When a name the game asks for and a name on the phone are the same file though their bytes differ:
# - the same letters in another Unicode form (NFC/NFD), in other case, or a Japanese sign written the Shift_JIS or the
#   Windows (CP932) way: both have the same key;
# - a name an unpacker read with the wrong code page and saved so, like UTF-8 read as GB18030 on a Chinese
#   phone ("bgmフィールド3" became "bgm銉曘偅銉笺儷銉�3", the "�" a byte it could not read): what the name
#   was before (unread) is the game's name in one of the code pages names are written in (written).
module AgvnNames
  # String#unicode_normalize loads lib/unicode_normalize of Ruby 3.1.3, which mkxp-z does not carry: the app copies it
  RUBY_LIB = File.join(File.dirname(__FILE__), 'ruby')
  # The same signs as a Shift_JIS decoder and Windows (CP932) write them
  JIS = ["\u301C\u2016\u2212\u00A2\u00A3\u00AC\u2014", "\uFF5E\u2225\uFF0D\uFFE0\uFFE1\uFFE2\u2015"].freeze
  # The code pages an unpacker may have read names with, and those a game's names may have been written in
  READ_AS = %w[GB18030 Windows-31J Big5 CP949 IBM437 Windows-1252 ISO-8859-1].freeze
  WRITTEN_IN = %w[UTF-8 Windows-31J GB18030 Big5 CP949].freeze
  # What a "�" stands for: one or two bytes beyond ASCII that the code page could not read
  LOST = '[\x80-\xFF]{1,2}'.b.freeze
  KEPT = 'bytes'.freeze
  @nfd = nil

  class << self
    # One spelling for names that differ only in case, in how accents and kana marks are stored, or in JIS signs
    def key(name)
      s = name.dup.force_encoding(Encoding::UTF_8)
      return s.b.downcase unless s.valid_encoding?
      return s.downcase if s.ascii_only?
      s = s.tr(*JIS)
      s = s.unicode_normalize(:nfd) if nfd?
      s = s.downcase(:fold)
      nfd? ? s.unicode_normalize(:nfd) : s
    end

    # [[code page, bytes], ...]: {name} as each code page of WRITTEN_IN writes it
    def written(name)
      s = name.dup.force_encoding(Encoding::UTF_8)
      return [[KEPT, s.b]] unless s.valid_encoding?
      WRITTEN_IN.filter_map do |page|
        [page, s.encode(page).b]
      rescue EncodingError
        nil
      end
    end

    # [[code page, bytes or pattern], ...]: the bytes {name}, a name on the phone, was before an unpacker read it with
    # each code page of READ_AS; a pattern where a byte was lost. A name that is not UTF-8 kept its bytes.
    def unread(name)
      s = name.dup.force_encoding(Encoding::UTF_8)
      return [] if s.ascii_only?
      return [[KEPT, s.b]] unless s.valid_encoding?
      pieces = s.split("\uFFFD", -1)
      READ_AS.filter_map do |page|
        bytes = pieces.map { |p| p.encode(page).b }
        next [page, bytes[0]] if bytes.size == 1
        [page, Regexp.new('\A'.b + bytes.map { |b| Regexp.escape(b) }.join(LOST) + '\z'.b, Regexp::NOENCODING)]
      rescue EncodingError
        nil
      end
    end

    # The name, then its letters beyond ASCII as code points, or its bytes when it is not UTF-8
    def show(name)
      s = name.dup.force_encoding(Encoding::UTF_8)
      return "\"#{s.scrub('?')}\" (byte #{s.unpack1('H*')})" unless s.valid_encoding?
      wide = s.each_char.reject(&:ascii_only?).map { |c| format('%04X', c.ord) }
      wide.empty? ? "\"#{s}\"" : "\"#{s}\" <#{wide.join(' ')}>"
    end

    private

    def nfd?
      return @nfd unless @nfd.nil?
      $LOAD_PATH << RUBY_LIB unless $LOAD_PATH.include?(RUBY_LIB)
      @nfd = "\u00E9".unicode_normalize(:nfd) == "e\u0301"
    rescue StandardError, ScriptError
      @nfd = false
    end
  end
end
