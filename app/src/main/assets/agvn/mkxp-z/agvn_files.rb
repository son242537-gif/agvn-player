# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's own scripts.
# Opens a game's file whose name on the phone is written differently from the name the game asks for. mkxp-z ignores
# case only for A-Z and compares the other letters byte by byte. Windows ignores case for every letter, and a tool that
# copied or unpacked the game may have stored accents and kana marks apart from their letters (Unicode NFD), or written
# a Japanese sign the other way (WAVE DASH for FULLWIDTH TILDE). When mkxp-z finds no such file, this looks in the
# game's folder for a name that is the same without those differences, and asks mkxp-z again with the phone's name.
# Each name found that way, and each file the folder does not have, goes to the file AGVN_RGSS_FILES_LOG names
# (tim-tep.txt in "Gửi nhật ký"). Anything it cannot do leaves the game's own error, still naming the game's line.
module AgvnFiles
  SELF = __FILE__
  # String#unicode_normalize loads lib/unicode_normalize of Ruby 3.1.3, which mkxp-z does not carry: the app copies it
  RUBY_LIB = File.join(File.dirname(SELF), 'ruby')
  # The same signs as a Shift_JIS decoder and Windows (CP932) write them
  JIS = ["\u301C\u2016\u2212\u00A2\u00A3\u00AC\u2014", "\uFF5E\u2225\uFF0D\uFFE0\uFFE1\uFFE2\u2015"].freeze
  LOG_LINES = 40
  NEAR = 3
  @found = {} # name the game asked for => the phone's name, or false when the folder has no such file
  @dirs = {} # folder => [[name, key], ...] in the order mkxp-z reads them
  @lines = 0
  @nfd = nil

  class << self
    # Calls the block with the name to open: the game's, or the phone's once mkxp-z found no file by the game's
    def open(name)
      file = name.is_a?(String) && !name.empty?
      known = file ? @found[name] : nil
      begin
        yield(known || name)
      rescue Errno::ENOENT => e
        other = file && known.nil? && find(name)
        raise utf8(e) unless other
        begin
          yield other
        rescue Errno::ENOENT
          @found[name] = false # mkxp-z does not have it either: the game's own error from now on
          log { "Vẫn không mở được: #{show(other)}" }
          raise utf8(e)
        end
      end
    rescue Exception => e # raised again as it came, without this file's lines: the error box names the game's line
      bt = e.frozen? ? nil : e.backtrace
      kept = bt && bt.reject { |line| line.start_with?(SELF) }
      e.set_backtrace(kept) if kept && !kept.empty?
      raise e
    end

    # The phone's name for the game's file, relative to the game's folder (mkxp-z's working folder); nil if none
    def find(name)
      parts = name.tr('\\', '/').split('/')
      return note(name, nil) if name.start_with?('/') || parts.first.to_s.include?(':')
      path = []
      parts.each_with_index do |part, i|
        next if part.empty? || part == '.'
        if part == '..'
          return note(name, nil) unless path.pop
          next
        end
        hit = entry(path, part, i == parts.size - 1)
        return note(name, nil, path, part) unless hit
        path << hit
      end
      path.empty? ? nil : note(name, path.join('/'))
    rescue StandardError, ScriptError
      nil
    end

    private

    # In folder {path}, the name with {part}'s key; for the last part, a file with any extension, as mkxp-z matches
    def entry(path, part, last)
      dir = path.empty? ? '.' : path.join('/')
      want = key(part)
      hit = listing(dir).find do |n, k|
        extension = last && Encoding.compatible?(k, want) && k.start_with?(want) && k[want.size] == '.'
        next false unless k == want || extension
        last ? File.file?(File.join(dir, n)) : File.directory?(File.join(dir, n))
      end
      hit && hit[0]
    end

    def listing(dir)
      @dirs[dir] ||= begin
        Dir.children(dir).map { |n| n = n.dup.force_encoding(Encoding::UTF_8); [n, key(n)] }
      rescue SystemCallError
        []
      end
    end

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

    def nfd?
      return @nfd unless @nfd.nil?
      $LOAD_PATH << RUBY_LIB unless $LOAD_PATH.include?(RUBY_LIB)
      @nfd = "\u00E9".unicode_normalize(:nfd) == "e\u0301"
    rescue StandardError, ScriptError
      @nfd = false
    end

    # Remembers the answer and tells the log once: the phone's name, or the names nearest to it where the search stopped
    def note(name, found, path = nil, part = nil)
      @found[name] = found || false
      log { found ? "Tìm ra: game hỏi #{show(name)}, trên máy là #{show(found)}" : missing(name, path, part) }
      found
    end

    def missing(name, path, part)
      text = "Không có: game hỏi #{show(name)}"
      return text unless part
      dir = path.empty? ? '.' : path.join('/')
      want = key(part)
      near = listing(dir).map { |n, k| [same_start(k, want), n] }.max_by(NEAR) { |c, _| c }.map { |_, n| show(n) }
      place = dir == '.' ? 'thư mục game' : show(dir)
      "#{text}; gần nhất trong #{place}: #{near.empty? ? 'trống' : near.join(', ')}"
    end

    def same_start(a, b)
      n = 0
      n += 1 while n < a.bytesize && n < b.bytesize && a.getbyte(n) == b.getbyte(n)
      n
    end

    # The name, then its letters beyond ASCII as code points, or its bytes when it is not UTF-8
    def show(name)
      s = name.dup.force_encoding(Encoding::UTF_8)
      return "\"#{s.scrub('?')}\" (byte #{s.unpack1('H*')})" unless s.valid_encoding?
      wide = s.each_char.reject(&:ascii_only?).map { |c| format('%04X', c.ord) }
      wide.empty? ? "\"#{s}\"" : "\"#{s}\" <#{wide.join(' ')}>"
    end

    # Writes the line the block makes, while the log has room
    def log
      file = ENV['AGVN_RGSS_FILES_LOG']
      return if file.nil? || file.empty? || @lines >= LOG_LINES
      @lines += 1
      line = yield
      File.open(file, 'a') { |f| f.puts(line) }
    rescue StandardError
      nil
    end

    # mkxp-z gives the message as bytes, which the error box then shows as one "�" for each byte of a Japanese name
    def utf8(e)
      msg = e.message.dup.force_encoding(Encoding::UTF_8)
      msg.valid_encoding? && !msg.ascii_only? ? e.exception(msg) : e
    rescue StandardError
      e
    end
  end
end

begin
  class << Audio
    %i[bgm_play bgs_play me_play se_play].each do |m|
      next unless method_defined?(m)
      native = :"agvn_files_#{m}"
      alias_method native, m
      define_method(m) { |name, *rest| AgvnFiles.open(name) { |n| send(native, n, *rest) } }
    end
  end

  class Bitmap
    alias_method :agvn_files_initialize, :initialize

    def initialize(*args)
      return AgvnFiles.open(nil) { agvn_files_initialize(*args) } unless args.size == 1
      AgvnFiles.open(args[0]) { |n| agvn_files_initialize(n) }
    end
  end

  class << Graphics
    alias_method :agvn_files_transition, :transition

    def transition(*args)
      return AgvnFiles.open(nil) { agvn_files_transition(*args) } if args.size < 2
      AgvnFiles.open(args[1]) { |n| agvn_files_transition(args[0], n, *args.drop(2)) }
    end

    if method_defined?(:play_movie)
      alias_method :agvn_files_play_movie, :play_movie

      def play_movie(name, *rest)
        AgvnFiles.open(name) { |n| agvn_files_play_movie(n, *rest) }
      end
    end
  end
rescue StandardError, ScriptError
  nil # a game this cannot wrap still starts, as before
end
