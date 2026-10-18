# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's own scripts.
# Opens a game's file whose name on the phone is written differently from the name the game asks for. mkxp-z ignores
# case only for A-Z and compares the other letters byte by byte. Windows ignores case for every letter, a tool that
# copied the game may have stored accents and kana marks apart from their letters (Unicode NFD), and an unpacker may
# have read the names with the wrong code page (agvn_names.rb). When mkxp-z finds no such file, this looks in the
# game's folder for the name the phone gave it, and asks mkxp-z again with that name.
# Each name found that way, and each file the folder does not have, goes to the file AGVN_RGSS_FILES_LOG names
# (tim-tep.txt in "Gửi nhật ký"). Anything it cannot do leaves the game's own error, still naming the game's line.
module AgvnFiles
  SELF = __FILE__
  LOG_LINES = 40
  NEAR = 3
  @found = {} # name the game asked for => the phone's name, or false when the folder has no such file
  @dirs = {} # folder => [[name, key], ...] in the order mkxp-z reads them
  @unread = {} # folder => [[name, AgvnNames.unread(name)], ...]
  @lines = 0

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
          log { "Vẫn không mở được: #{AgvnNames.show(other)}" }
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
      return note(name) if name.start_with?('/') || parts.first.to_s.include?(':')
      path = []
      how = []
      parts.each_with_index do |part, i|
        next if part.empty? || part == '.'
        if part == '..'
          return note(name) unless path.pop
          next
        end
        hit, garbled = entry(path, part, i == parts.size - 1)
        return note(name, path: path, part: part) unless hit
        how << garbled if garbled
        path << hit
      end
      path.empty? ? nil : note(name, path.join('/'), how: how.uniq)
    rescue StandardError, ScriptError
      nil
    end

    private

    # In folder {path}, the phone's name for {part}, and how an unpacker garbled it if it did. For the last part, a file
    # with any extension, as mkxp-z matches.
    def entry(path, part, last)
      dir = path.empty? ? '.' : path.join('/')
      want = AgvnNames.key(part)
      hit = listing(dir).find do |n, k|
        extension = last && Encoding.compatible?(k, want) && k.start_with?(want) && k[want.size] == '.'
        (k == want || extension) && kind?(dir, n, last)
      end
      hit ? [hit[0], nil] : garbled(dir, part, last)
    end

    # The name an unpacker made of {part}'s bytes in {dir}, and how ("UTF-8 đọc thành GB18030"): the first
    # name read without loss, else the only one read with a lost byte (the same name with other extensions aside)
    def garbled(dir, part, last)
      return nil if part.ascii_only?
      wants = AgvnNames.written(part)
      hits = []
      (@unread[dir] ||= listing(dir).map { |n, _| [n, AgvnNames.unread(n)] }).each do |n, forms|
        tails = last ? ['', File.extname(n).b] : ['']
        forms.each do |page, form|
          w = wants.find do |_, bytes|
            tails.any? { |t| form.is_a?(Regexp) ? form.match?(bytes + t) : form == bytes + t }
          end
          next unless w && kind?(dir, n, last)
          hits << [n, page == AgvnNames::KEPT ? "#{w[0]} giữ nguyên byte" : "#{w[0]} đọc thành #{page}", form]
          break
        end
      end
      sure = hits.find { |_, _, form| !form.is_a?(Regexp) }
      return sure.first(2) if sure
      hits.map { |n, _, _| n.sub(/\.[^.]*\z/, '') }.uniq.size == 1 ? hits.first.first(2) : nil
    end

    def kind?(dir, name, last)
      last ? File.file?(File.join(dir, name)) : File.directory?(File.join(dir, name))
    end

    def listing(dir)
      @dirs[dir] ||= begin
        Dir.children(dir).map { |n| n = n.dup.force_encoding(Encoding::UTF_8); [n, AgvnNames.key(n)] }
      rescue SystemCallError
        []
      end
    end

    # Remembers the answer and tells the log once: the phone's name, or the names nearest to it where the search stopped
    def note(name, found = nil, how: [], path: [], part: nil)
      @found[name] = found || false
      log do
        next missing(name, path, part) unless found
        garbled = how.empty? ? '' : "; tên bị giải nén sai bảng mã: #{how.join(', ')}"
        "Tìm ra: game hỏi #{AgvnNames.show(name)}, trên máy là #{AgvnNames.show(found)}#{garbled}"
      end
      found
    end

    def missing(name, path, part)
      text = "Không có: game hỏi #{AgvnNames.show(name)}"
      return text unless part
      dir = path.empty? ? '.' : path.join('/')
      want = AgvnNames.key(part)
      near = listing(dir).map { |n, k| [same_start(k, want), n] }.max_by(NEAR) { |c, _| c }
      near = near.empty? ? 'trống' : near.map { |_, n| AgvnNames.show(n) }.join(', ')
      "#{text}; gần nhất trong #{dir == '.' ? 'thư mục game' : AgvnNames.show(dir)}: #{near}"
    end

    def same_start(a, b)
      n = 0
      n += 1 while n < a.bytesize && n < b.bytesize && a.getbyte(n) == b.getbyte(n)
      n
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
