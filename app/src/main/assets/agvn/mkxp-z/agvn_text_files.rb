# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's scripts.
# A file a script opens to read as text ("r", the default, without "b") reads as on Windows, where RPG Maker games are
# made and played: each "\r\n" of the file reaches the script as "\n". Ruby reads text mode so on Windows, and gave
# the "\r" too on the phone. Lona RPG's JSON reader skips spaces, "\n" and "\t" but not "\r": at the first one it read
# the same "[" again and again, until "stack level too deep" (log of 04/11/2026; 326 of its 338 scripts end their lines
# with "\r\n", as Windows editors write them).
# Ruby's own text mode ("rt") does it here: File.open, File.new, open, File.read, File.readlines and File.foreach. A
# lone "\r" becomes "\n" as well (Windows keeps it; text files hardly have one). Writing, reading and writing ("r+"),
# binary files ("rb", File.binread), a mode given as a number, and a call with its own binmode, textmode or newline
# option stay as they were.
module AgvnTextFiles
  # Options that set the mode, or the newlines, their own way: "t" would contradict them
  OWN_MODE = %i[binmode textmode flags newline universal_newline crlf_newline cr_newline lf_newline].freeze

  class << self
    # {mode} with "t" when it reads only, as text; any other mode as it is
    def mode(mode)
      return 'rt' if mode.nil?
      return mode unless mode.is_a?(String)
      fmode, enc = mode.split(':', 2)
      return mode unless fmode == 'r'
      enc ? "rt:#{enc}" : 'rt'
    end

    # A call's keyword options with a text mode, unless they set the mode their own way
    def opts(opts)
      return opts if OWN_MODE.any? { |k| opts.key?(k) }
      m = mode(opts[:mode])
      m.equal?(opts[:mode]) ? opts : opts.merge(mode: m)
    end

    # File.new's arguments with a text mode: the mode comes second, or among the options
    def args(args, opts)
      return [args, opts] unless args[0].is_a?(String) || args[0].respond_to?(:to_path)
      return [args, opts] if OWN_MODE.any? { |k| opts.key?(k) }
      return [args, self.opts(opts)] if opts.key?(:mode)
      return [[args[0], 'rt', *args.drop(1)], opts] if args.size == 1
      [[args[0], mode(args[1]), *args.drop(2)], opts]
    end
  end

  # File.open, File.new and open come here before the file opens; File.open runs its block afterwards, elsewhere
  module Open
    def initialize(*args, **opts, &block)
      text, text_opts = AgvnTextFiles.args(args, opts)
      return super(*args, **opts, &block) if text.equal?(args) && text_opts.equal?(opts)
      begin
        super(*text, **text_opts, &block)
      rescue ArgumentError
        super(*args, **opts, &block) # the game's own arguments, and their own error if they have one
      end
    end
  end

  module Read
    %i[read readlines].each do |m|
      define_method(m) do |name, *rest, **opts|
        text = AgvnTextFiles.opts(opts)
        return super(name, *rest, **opts) if text.equal?(opts)
        begin
          super(name, *rest, **text)
        rescue ArgumentError
          super(name, *rest, **opts)
        end
      end
    end

    # No second try here: the game's block runs inside, and its own error must not run it again
    def foreach(name, *rest, **opts, &block)
      super(name, *rest, **AgvnTextFiles.opts(opts), &block)
    end
  end
end

begin
  File.prepend(AgvnTextFiles::Open)
  IO.singleton_class.prepend(AgvnTextFiles::Read)
rescue StandardError
  nil # a Ruby without them reads as before
end
