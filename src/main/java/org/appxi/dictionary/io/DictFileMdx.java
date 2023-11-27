package org.appxi.dictionary.io;

import org.appxi.dictionary.Dictionary;
import org.appxi.util.ext.Compression;
import org.appxi.util.ext.Node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

public class DictFileMdx {
    public static Node<Dictionary.Entry> read(File file) {
        final String dictName = file.getName().replaceFirst("[.][^.]+$", "").strip();
        final Node<Dictionary.Entry> entryTree = new Node<>(Dictionary.Entry.ofCategory(dictName));
        //
        try {
            final DictFileMdx.MdxFile md = new DictFileMdx.MdxFile(file);

            for (int i = 0; i < md.getNumberEntries(); i++) {
                final String word0 = md.getEntryAt(i).strip();
                //
                if (word0.contains("製作說明") || word0.contains("總目錄")
                    || word0.startsWith(dictName) || word0.contains(dictName)) {
                    System.err.println("SKIP WORD: " + word0);
                    continue;
                }

                String word1 = word0
                        .replace("[", "")
                        .replace("]", "")
                        .replaceAll("[【】“”\"'‘’\u0092]+", "")
                        //
                        .strip();
                if (word1.startsWith(dictName) || word1.contains(dictName)) {
                    System.err.println("SKIP WORD: " + word1);
                    continue;
                }

                if (word1.startsWith(":")) {
                    System.err.println("FAKE WORD: " + word1);
                    continue;
                }

                //
                String text = md.getRecordAt(i)
//                            .replace("<a>", "")
//                            .replace("</a>", "")
                        //
                        .strip();
                if (text.isBlank()) {
                    System.out.println();
                }
                text = text.replace(" class=MsoNormal", "")
                        .replace("class=MsoNormal", "")
                        .replace(" style='font-family:\"Microsoft Himalaya\"'", "")
                        .replace("style='font-family:\"Microsoft Himalaya\"'", "")
                        .replace("<style type=\"text/css\">p{margin:0}</style>", "")
                        .replace("<link rel=\"stylesheet\" type=\"text/css\" href=\"sf_ecce.css\"/>", "")
                        .replace("<span style=\"float:right;\"><A HREF=\"entry://00 總目錄\">總目錄</a></span><hr color=#AA8A57>", "\r\n")
                        .replace("<a href=\"entry://:about,首页\">返回首页</a>", "")
                        .replaceAll("^(<br>)+|(<br>)+$", "")
                        .replaceAll("<br><br>From：.*<br>(\r\n|[\r\n])", "")
                        .replaceAll("<br>", "\r\n")
                        //
                        .strip();


                if (text.startsWith(word0 + "　") || text.startsWith(word0 + " ")
                    || text.startsWith(word0 + "\r\n") || text.startsWith(word0 + "\r") || text.startsWith(word0 + "\n")) {
                    text = text.substring(word0.length()).strip();
                }

                if (text.startsWith(word1 + "　") || text.startsWith(word1 + " ")
                    || text.startsWith(word1 + "\r\n") || text.startsWith(word1 + "\r") || text.startsWith(word1 + "\n")) {
                    text = text.substring(word1.length()).strip();
                }

                if (text.contains(">上一条</a>") || text.contains(">下一条</a>")) {
                    int idx = text.lastIndexOf("\n");
                    if (idx != -1) {
                        String str = text.substring(idx);
                        if (str.contains(">上一条</a>") || str.contains(">下一条</a>")) {
                            text = text.substring(0, idx).strip();
                        }
                    }
                }
                text = text.replaceAll("(\r\n|[\r\n])+", "\r\n")
                        .replaceAll("<span(\r\n|[\r\n])", "<span ")
//                            .replace("\\n　", "\r\n")
//                            .replace("\\n", "\r\n")
                        .replaceAll("　　", "\r\n");
                text = text.lines()
                        .map(String::strip)
                        .collect(Collectors.joining("\r\n"))
////                    ;
////                    text = Jsoup.parse(text).body().html()
                        .replaceAll("([^.!?\"'。！？“”‘’])(\r\n|[\r\n])", "$1 ")
                        .replace(" style='font-family:\"微软雅黑\",\"sans-serif\"'", "")
                        .replace(" style='font-family:\"Calibri\",\"sans-serif\"'", "")
                        .replace("<span class=\"word\"><span class=\"return-phrase\"><span class=\"l\"><span class=\"i\">" + word0 + "</span></span></span><span class=\"phone\"></span>", "")
                ;
                //
                Dictionary.Entry entry = Dictionary.Entry.of(word1, text);
                entryTree.add(entry);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        //
        return entryTree;
    }

    static void Log(Object... o) {
        StringBuilder msg = new StringBuilder("fatal_log_mdict : ");
        if (o != null)
            for (Object value : o) {
                if (value instanceof Exception) {
                    ByteArrayOutputStream s = new ByteArrayOutputStream();
                    PrintStream p = new PrintStream(s);
                    ((Exception) value).printStackTrace(p);
                    msg.append(s);
                }
                msg.append(value).append(" ");
            }
        System.out.println(msg);
    }

    public static void parseMdxFile(File file, BiConsumer<MdxFile, Integer> consumer) throws Exception {
        final MdxFile mdxFile = new MdxFile(file);
        for (int i = 0; i < mdxFile.getNumberEntries(); i++) {
            consumer.accept(mdxFile, i);
        }
    }

    public static void parseMdxFile(File file, Consumer<MdxFile> mdxFileConsumer, BiConsumer<String, String> wordTextConsumer) throws Exception {
        final MdxFile mdxFile = new MdxFile(file);
        mdxFileConsumer.accept(mdxFile);
        for (int i = 0; i < mdxFile.getNumberEntries(); i++) {
            final String word = mdxFile.getEntryAt(i).strip();
            final String text = mdxFile.getRecordAt(i).strip();
            wordTextConsumer.accept(word, text);
        }
    }

    abstract static class MdbFile {
        //TODO Standardize
        /**
         * 标准究竟是怎样的呢？ 添加@_，俩符号会在词块trailer和header中出现，不参与排序。暂时禁用了isCompat标志
         */
        public final static Pattern replaceReg = Pattern.compile("[ @_=&:$/.,\\-'()\\[\\]#<>!\\n]");
        //        public final static Pattern replaceReg2 = Pattern.compile("[ \\-]");
        public final static Pattern numSuffixedReg = Pattern.compile(".+?([0-9]+)");
        public final static Pattern markerReg = Pattern.compile("`([\\w\\W]{1,3}?)`");// for `1` `2`...
        public final static String linkRenderStr = "@@@LINK=";
        public final static HashMap<String, byte[]> linkRenderByts = new HashMap<>();
//        public final static Pattern requestPattern = Pattern.compile("\\?.*?=.*");
//        public final static String fullpageString = "<!DOCTYPE HTML>";
//        public final static Pattern fullpagePattern = Pattern.compile("^<!DOCTYPE HTML>", Pattern.CASE_INSENSITIVE);

        public final static Pattern imageReg = Pattern.compile("\\.jpg|\\.bmp|\\.eps|\\.gif|\\.png|\\.tif|\\.tiff|\\.svg|\\.jpe|\\.jpeg|\\.ico|\\.tga|\\.pic$", Pattern.CASE_INSENSITIVE);
        public final static Pattern htmlReg = Pattern.compile("\\.html$", Pattern.CASE_INSENSITIVE);
        //        public final static Pattern mobiReg = Pattern.compile("\\.mobi|\\.azw|\\.azw3$", Pattern.CASE_INSENSITIVE);
        public final static Pattern soundReg = Pattern.compile("\\.mp3|\\.ogg|\\.wav|\\.spx$", Pattern.CASE_INSENSITIVE);
        public final static Pattern videoReg = Pattern.compile("\\.mp4|\\.avi$", Pattern.CASE_INSENSITIVE);

        public static String lineBreakText = "\r\n\0";
        public byte[] linkRenderByt;
        protected StringBuilder univeral_buffer;
        protected File file;
        protected long ReadOffset;
        /**
         * 0=no cache; 1=lru cache; 2=unlimited
         */
        protected int FileCacheStrategy = 0;
        ByteArrayInputStream preparedStream;

        final static byte[] _zero4 = new byte[]{0, 0, 0, 0};
        final static byte[] _1zero3 = new byte[]{1, 0, 0, 0};
        final static byte[] _2zero3 = new byte[]{2, 0, 0, 0};
        final static String emptyStr = "";
        final static String invalid_format = "unrecognized format : ";
        public Boolean isCompact = true;
        public Boolean isStripKey = true;
        protected Boolean isKeyCaseSensitive = false;
        /**
         * encryption flag
         * 0x00 - no encryption
         * 0x01 - encrypt record block
         * 0x02 - encrypt key info block
         */
        int _encrypt;
        protected Charset _charset;

        String _encoding = "UTF-16LE";

        protected int delimiter_width = 1;
        String _passcode = "";

        float _version;
        int _number_width;
        protected long _num_entries;

        public long getNumberEntries() {
            return _num_entries;
        }

        long _num_key_blocks;

        protected long _num_record_blocks;

        RBTree<myCpr<Integer, Integer>> accumulation_blockId_tree = new RBTree<>();
        long _key_block_size, _key_block_info_size, _key_block_info_decomp_size, _record_block_info_size, _record_block_size;

        int _key_block_offset;
        long _record_block_offset;

        KeyInfo[] _key_block_info_list;
        RecordInfo[] _recordInfo_list;

        int rec_decompressed_size;
        long maxComRecSize;
        long maxDecompressedSize;
        //public long maxComKeyBlockSize;
        /**
         * Maximum size of one record block
         */
        public long maxDecomKeyBlockSize;
        public long maxComKeyBlockSize;
        /**
         * data buffer that holds one record bock of maximum possible size for this dictionary
         */

        protected Object tag;

        protected DataInputStream getStreamAt(long at, boolean forceReal) throws IOException {
            //SU.Log("getStreamAt", at, this);
            DataInputStream data_in1;
            //forceReal = true;
            if (preparedStream != null) {
                preparedStream.reset();
                data_in1 = new DataInputStream(preparedStream);
            } else if (forceReal || FileCacheStrategy == 0) {
                //return new BufferedInputStream(new FileInputStream(f));
                data_in1 = new DataInputStream(new FileInputStream(file));
            } else {
                data_in1 = new DataInputStream(new LruInputStream());
            }
            at += ReadOffset;
            if (at > 0) {
                long yue = 0;
                while (yue < at) {
                    yue += data_in1.skip(at - yue);
                }
            }
            return data_in1;
        }

        public void prepareFileStream() throws IOException {
            //return new BufferedInputStream(new FileInputStream(f));
            InputStream input = new FileInputStream(file);
            byte[] buffer = new byte[input.available()];
            input.read(buffer);
            preparedStream = new ByteArrayInputStream(buffer);
        }

        public Map<Integer, byte[]> file_cache_map;

        /**
         * It turned out that disk accessing isn't the bottleneck for speed.
         */
        class LruInputStream extends InputStream {
            long skipped = 0;
            private InputStream input_real;

            @Override
            public int read() {
                return 0;
            }

            @Override
            public long skip(long n) {
                skipped += n;
                return skipped;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int token;
                final int BlockSize = LinkastReUsageHashMap.BlockSize;
                int newStart = (int) (skipped) / BlockSize * BlockSize;
                int boost_ahead = (int) (skipped - newStart);
                int end = (int) (skipped + len);
                int newEnd = (int) (Math.ceil(1.0 * end / BlockSize) * BlockSize);
                int poost_atail = newEnd - end;
                int newLen = len + boost_ahead + poost_atail;
                long toSkip = skipped - boost_ahead;
                if (file_cache_map == null) {
                    int cache_size = FileCacheStrategy == 1 ? LinkastReUsageHashMap.BlockCacheSize : 0;
                    int perBlLockSize = BlockSize / 1024;
                    file_cache_map = Collections.synchronizedMap(new LinkastReUsageHashMap<>((cache_size == 0 ? (int) file.length() : cache_size) / perBlLockSize, cache_size, perBlLockSize));
                } else {
                    ArrayList<byte[]> arr = null;
                    token = (int) toSkip;
                    while (token < newEnd) {
                        byte[] tmpData = file_cache_map.get(token);
                        if (tmpData == null) {
                            arr = null;
                            break;
                        } else {
                            if (arr == null) {
                                arr = new ArrayList<>(1 + len / 1024);
                            }
                            arr.add(tmpData);
                            token += BlockSize;
                        }
                    }
                    if (arr != null) {
                        Log("取");
                        int length = 0;
                        int size = arr.size() - 1;
                        for (int i = 0; i <= size; i++) {
                            byte[] dataI = arr.get(i);
                            int copyStart = 0;
                            int copyLen = dataI.length;
                            if (i == 0) {
                                copyStart = boost_ahead;
                                copyLen -= boost_ahead;
                            } else if (i == size) {
                                copyLen -= poost_atail;
                            }
                            if (off + length + copyLen > b.length) {
                                copyLen = b.length - off - length;
                            }
                            System.arraycopy(dataI, copyStart, b, off + length, copyLen);
                            length += copyLen;
                        }
                        return length;
                    }
                }
                if (input_real == null) {
                    //return new BufferedInputStream(new FileInputStream(f));
                    input_real = new FileInputStream(file);
                }
                if (toSkip > 0) {
                    long yue = 0;
                    while (yue < toSkip) {
                        yue += input_real.skip(toSkip - yue);
                    }
                }
                int essenceLen = len;
                int length = 0;
                if (boost_ahead != 0) {
                    byte[] head = new byte[BlockSize];
                    input_real.read(head, 0, BlockSize);
                    int delta = BlockSize - boost_ahead;
                    off += delta;
                    essenceLen -= delta;
                    System.arraycopy(head, boost_ahead, b, 0, delta);
                    file_cache_map.put(newStart, head);
                }
                if (poost_atail != 0) {
                    essenceLen -= BlockSize - poost_atail;
                }
                length = input_real.read(b, off, essenceLen);
                if (poost_atail != 0) {
                    byte[] tail = new byte[BlockSize];
                    input_real.read(tail, 0, BlockSize);
                    System.arraycopy(tail, 0, b, off + essenceLen, BlockSize - poost_atail);
                    file_cache_map.put(newEnd - BlockSize, tail);
                }

                token = newStart;
                if (boost_ahead != 0) {
                    token += BlockSize;
                }
                len = newEnd;
                if (poost_atail != 0) {
                    newEnd -= BlockSize;
                }
                while (token < newEnd) {
                    Log("存");
                    byte[] tmpData = new byte[BlockSize];
                    System.arraycopy(b, (int) (token - skipped), tmpData, 0, BlockSize);

                    file_cache_map.put(token, tmpData);
                    token += BlockSize;
                }
                return len;
            }
        }


        protected HashMap<String, String[]> _stylesheet = new HashMap<>();
        public int lenSty = 0;

        //构造
        MdbFile(File file, int pseudoInit, StringBuilder buffer, Object tag) throws IOException {
            //![0]File in
            this.file = file;

            this.tag = tag;

            univeral_buffer = buffer;
            if (pseudoInit != 1) {
                if (pseudoInit == 2) {
                    prepareFileStream();
                }
                init(getStreamAt(0, true));
            }
        }

        MdbFile(MdbFile master, DataInputStream data_in) throws IOException {
            file = master.file;
            _header_tag = (HashMap<String, String>) master._header_tag.clone();
            _header_tag.remove("hasSlavery");
            init(data_in);
        }

        protected void init(DataInputStream data_in) throws IOException {
            //![1]read_header
            // number of bytes of header text
            if (data_in == null) return;
            byte[] itemBuf = new byte[4];
            data_in.read(itemBuf, 0, 4);
            int header_bytes_size = ByteUtils.toInt(itemBuf, 0);
            _key_block_offset = 4 + header_bytes_size + 4;
            if (header_bytes_size < 0 || header_bytes_size > 2621440) {
                throw new IOException(invalid_format + "invalid header size " + _key_block_offset);
            }
            byte[] header_bytes = new byte[header_bytes_size];
            data_in.read(header_bytes, 0, header_bytes_size);

            // 4 bytes: adler32 checksum of header, in little endian
            itemBuf = new byte[4];
            data_in.read(itemBuf, 0, 4);
            int alder32 = getInt(itemBuf[3], itemBuf[2], itemBuf[1], itemBuf[0]);
            //assert alder32 == (BU.calcChecksum(header_bytes)& 0xffffffff);
            //        if ((calcChecksum(header_bytes) & 0xffffffff) != alder32)
            //            throw new IOException(invalid_format + "invalid header checksum");

            //data_in.skipBytes(4);
            //不必关闭文件流 data_in

            Pattern re = Pattern.compile("(\\w+)=[\"](.*?)[\"]", Pattern.DOTALL);
            String headerString = new String(header_bytes, StandardCharsets.UTF_16LE);
            //SU.Log("headerString::", headerString);
            Matcher m = re.matcher(headerString);
            if (_header_tag == null) {
                _header_tag = new HashMap<>();
            }
            while (m.find()) {
                _header_tag.put(m.group(1), m.group(2));
            }

            String valueTmp = _header_tag.get("Compact");
            if (valueTmp == null)
                valueTmp = _header_tag.get("Compat");
            if (valueTmp != null)
                isCompact = !(valueTmp.equals("No"));


            valueTmp = _header_tag.get("StripKey");
            if (valueTmp != null)
                isStripKey = valueTmp.length() == 3;

            valueTmp = _header_tag.get("KeyCaseSensitive");
            if (valueTmp != null)
                isKeyCaseSensitive = valueTmp.length() == 3;

            valueTmp = _header_tag.get("Encoding");
            if (valueTmp != null && !valueTmp.equals(""))
                _encoding = valueTmp.toUpperCase();

            if (_encoding.equals("GBK") || _encoding.equals("GB2312")) _encoding = "GB18030";// GB18030 > GBK > GB2312
            if (_encoding.equals("")) _encoding = "UTF-8";
            if (_encoding.equals("UTF-16")) _encoding = "UTF-16LE"; //INCONGRUENT java charset

            _charset = Charset.forName(_encoding);
            postGetCharset();

            linkRenderByt = RerouteHeader_forName(_encoding, _charset);

            if (_encoding.startsWith("UTF-16")) {
                delimiter_width = 2;
            } else {
                delimiter_width = 1;
            }


            String EncryptedFlag = _header_tag.get("Encrypted");
            if (EncryptedFlag != null) {
                _encrypt = IntUtils.parsint(EncryptedFlag, -1);
                if (_encrypt < 0) {
                    _encrypt = EncryptedFlag.equals("Yes") ? 1 : 0;
                }
            }

            // stylesheet attribute if present takes form of:
            //   style_number # 1-255
            //   style_begin  # or ''
            //   style_end    # or ''
            // store stylesheet in dict in the form of
            // {'number' : ('style_begin', 'style_end')}

            if (_header_tag.containsKey("StyleSheet")) {
                String[] lines = _header_tag.get("StyleSheet").split("\n");
                for (int i = 0; i < lines.length; i += 3) {
                    if (lines[i].length() > 0) { //todo
                        Log("_stylesheet.put", lines[i], lines[i].length());
                        _stylesheet.put(lines[i], new String[]{i + 1 < lines.length ? lines[i + 1] : "", i + 2 < lines.length ? lines[i + 2] : ""});
                        lenSty++;
                    }
                }
            }

            _version = Float.parseFloat(_header_tag.get("GeneratedByEngineVersion"));

            _number_width = _version < 2.0 ? 4 : 8;

            //![1]HEADER 分析完毕
            //![2]_read_keys_info START
            //stst = System.currentTimeMillis();
            //size (in bytes) of previous 5 or 4 numbers (can be encrypted)
            int num_bytes = _version >= 2 ? 8 * 5 + 4 : 4 * 4;
            itemBuf = new byte[num_bytes];
            data_in.read(itemBuf, 0, num_bytes);
            //data_in.close();
            ByteBuffer sf = ByteBuffer.wrap(itemBuf);

            //TODO: pureSalsa20.py decryption
            if (_encrypt == 1) {
                if (_passcode.equals(emptyStr)) throw new IllegalArgumentException("_passcode未输入");
            }
            _num_key_blocks = _read_number(sf);                                           // 1
            _num_entries = _read_number(sf);                                          // 2
            if (_version >= 2.0) {
                _key_block_info_decomp_size = _read_number(sf);
            }      //[3]
            _key_block_info_size = _read_number(sf);                                  // 4
            _key_block_size = _read_number(sf);                                       // 5

            //前 5 个数据的 adler checksum
            if (_version >= 2.0) {
                //int adler32 = BU.calcChecksum(itemBuf,0,num_bytes-4);
                //assert adler32 == (sf.getInt()& 0xffffffff);
            }

            _key_block_offset += num_bytes + _key_block_info_size;

            //assert(_num_key_blocks == _key_block_info_list.length);

            _record_block_offset = _key_block_offset + _key_block_size;


            readKeyBlockInfo(data_in);
        }

        protected void postGetCharset() {
        }

        public byte[] RerouteHeader_forName(String encoding, Charset _charset) {
            linkRenderByt = linkRenderByts.get(encoding);
            if (linkRenderByt == null) {
                linkRenderByts.put(encoding, linkRenderByt = linkRenderStr.getBytes(_charset));
            }
            return linkRenderByt;
        }


        void readKeyBlockInfo(DataInputStream data_in1) {
            // read key block info, which comprises each key_block's:
            //1.(starting && ending words'shrinkedText,in the form of shrinkedTextSize-shrinkedText.Name them as headerText、tailerText)、
            //2.(compressed && decompressed size,which also have version differences, occupying either 4 or 8 bytes)
            boolean responsibleForDataIn = data_in1 == null;
            try {
                if (responsibleForDataIn) data_in1 = getStreamAt(_key_block_offset - _key_block_info_size, true);
                byte[] itemBuf = new byte[(int) _key_block_info_size];
                data_in1.read(itemBuf, 0, (int) _key_block_info_size);
                if (responsibleForDataIn) data_in1.close();
                decodeKeyBlockInfo(itemBuf);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        void decodeKeyBlockInfo(byte[] key_block_info_compressed) {
            KeyInfo[] _key_block_info_list = new KeyInfo[(int) _num_key_blocks];
            //block_blockId_search_list = new String[(int) _num_key_blocks];
            byte[] key_block_info = null;
            int BlockOff = 0;
            int BlockLen = 0;//(int) infoI.key_block_decompressed_size;
            if (_version >= 2) {
                //处理 Ripe128md 加密的 key_block_info
                if (_encrypt == 2) {
                    try {
                        key_block_info_compressed = ByteUtils._mdx_decrypt(key_block_info_compressed);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //!!!getInt CAN BE NEGTIVE ,INCONGRUENT to python CODE
                //!!!MAY HAVE BUG
                //int adler32 = getInt(key_block_info_compressed[4],key_block_info_compressed[5],key_block_info_compressed[6],key_block_info_compressed[7]);

                //解压开始
                switch (key_block_info_compressed[0] | key_block_info_compressed[1] << 8 | key_block_info_compressed[2] << 16 | key_block_info_compressed[3] << 32) {
                    case 0://no compression
                        key_block_info = key_block_info_compressed;
                        BlockOff = 8;
                        BlockLen = key_block_info_compressed.length - 8;
                        break;
                    case 1:
                        key_block_info = lzo_decompress(key_block_info_compressed, 8);
                        break;
                    case 2:
                        key_block_info = zlib_decompress(key_block_info_compressed, 8);
                        BlockLen = key_block_info.length;
                        break;
                }
                //assert(adler32 == (BU.calcChecksum(key_block_info) ));
                //ripemd128.printBytes(key_block_info,0, key_block_info.length);
            } else
                key_block_info = key_block_info_compressed;
            // decoding……
            long key_block_compressed_size = 0;
            int accumulation_ = 0;//how many entries before one certain block.for construction of a list.
            //遍历blocks
            int bytePointer = 0;
            for (int i = 0; i < _key_block_info_list.length; i++) {
                int textbufferST, textbufferLn;
                accumulation_blockId_tree.insert(new myCpr<>(accumulation_, i));
                //read in number of entries in current key block
                if (_version < 2) {
                    _key_block_info_list[i] = new KeyInfo(ByteUtils.toInt(key_block_info, BlockOff + bytePointer), accumulation_);
                    bytePointer += 4;
                } else {
                    //com.knziha.plod.CMN.show(key_block_info_compressed.length+":"+key_block_info.length+":"+bytePointer);
                    _key_block_info_list[i] = new KeyInfo(ByteUtils.toLong(key_block_info, BlockOff + bytePointer), accumulation_);
                    bytePointer += 8;
                }
                KeyInfo infoI = _key_block_info_list[i];
                accumulation_ += infoI.num_entries;
                //com.knziha.plod.CMN.show("infoI.num_entries::"+infoI.num_entries);
                //![0] head word text
                int text_head_size;
                if (_version < 2)
                    text_head_size = key_block_info[BlockOff + bytePointer++] & 0xFF;
                else {
                    text_head_size = ByteUtils.toChar(key_block_info, BlockOff + bytePointer);
                    bytePointer += 2;
                }
                textbufferST = bytePointer;
                if (!_encoding.startsWith("UTF-16")) {
                    textbufferLn = text_head_size;
                    if (_version >= 2)
                        bytePointer++;
                } else {
                    textbufferLn = text_head_size * 2;
                    if (_version >= 2)
                        bytePointer += 2;
                }

                infoI.headerKeyText = new byte[textbufferLn];
                System.arraycopy(key_block_info, BlockOff + textbufferST, infoI.headerKeyText, 0, textbufferLn);


                bytePointer += textbufferLn;


                //![1]  tail word text
                int text_tail_size;
                if (_version < 2)
                    text_tail_size = key_block_info[BlockOff + bytePointer++] & 0xFF;
                else {
                    text_tail_size = ByteUtils.toChar(key_block_info, BlockOff + bytePointer);
                    bytePointer += 2;
                }
                textbufferST = bytePointer;

                textbufferLn = text_tail_size * delimiter_width;
                if (_version >= 2)
                    bytePointer += delimiter_width;

                infoI.tailerKeyText = new byte[textbufferLn];

                System.arraycopy(key_block_info, BlockOff + textbufferST, infoI.tailerKeyText, 0, textbufferLn);

                bytePointer += textbufferLn;

                //show(infoI.tailerKeyText+"~tailerKeyText");

                infoI.key_block_compressed_size_accumulator = key_block_compressed_size;
                if (_version < 2) {//may reduce
                    infoI.key_block_compressed_size = ByteUtils.toInt(key_block_info, BlockOff + bytePointer);
                    key_block_compressed_size += infoI.key_block_compressed_size;
                    bytePointer += 4;
                    infoI.key_block_decompressed_size = ByteUtils.toInt(key_block_info, BlockOff + bytePointer);
                    maxDecomKeyBlockSize = Math.max(infoI.key_block_decompressed_size, maxDecomKeyBlockSize);
                    maxComKeyBlockSize = Math.max(infoI.key_block_compressed_size, maxComKeyBlockSize);
                    bytePointer += 4;
                } else {
                    infoI.key_block_compressed_size = ByteUtils.toLong(key_block_info, BlockOff + bytePointer);
                    key_block_compressed_size += infoI.key_block_compressed_size;
                    bytePointer += 8;
                    infoI.key_block_decompressed_size = ByteUtils.toLong(key_block_info, BlockOff + bytePointer);
                    maxDecomKeyBlockSize = Math.max(infoI.key_block_decompressed_size, maxDecomKeyBlockSize);
                    maxComKeyBlockSize = Math.max(infoI.key_block_compressed_size, maxComKeyBlockSize);

                    bytePointer += 8;
                }
                //com.knziha.plod.CMN.show("maxDecomKeyBlockSize: "+infoI.key_block_decompressed_size);
                //com.knziha.plod.CMN.show("infoI.key_block_decompressed_size::"+infoI.key_block_decompressed_size);
                //com.knziha.plod.CMN.show("infoI.key_block_compressed_size::"+infoI.key_block_compressed_size);

                //block_blockId_search_list[i] = infoI.headerKeyText;
            }
            key_block_info = null;
            //assert(accumulation_ == self._num_entries)
            this._key_block_info_list = _key_block_info_list;
        }

        long decodeRecordBlockSize(DataInputStream data_in1) {
            if (_num_record_blocks == 0) {
                try {
                    boolean responsibleForDataIn = data_in1 == null;
                    if (responsibleForDataIn) data_in1 = getStreamAt(_record_block_offset, true);
                    _num_record_blocks = _read_number(data_in1);
                    long num_entries = _read_number(data_in1);
                    _record_block_info_size = _read_number(data_in1);
                    _record_block_size = _read_number(data_in1);
                    if (responsibleForDataIn) data_in1.close();
                } catch (IOException ignored) {
                }
            }
            return _num_record_blocks;
        }

        void decode_record_block_header() throws IOException {
            //![3]Decode_record_block_header
            //long start = System.currentTimeMillis();
            DataInputStream data_in1 = getStreamAt(_record_block_offset, true);

            _num_record_blocks = _read_number(data_in1);
            long num_entries = _read_number(data_in1);
            //assert(num_entries == _num_entries);
            long record_block_info_size = _read_number(data_in1);
            _record_block_size = _read_number(data_in1);

            //record block info section
            RecordInfo[] recordInfo_list = new RecordInfo[(int) _num_record_blocks];
            //int size_counter = 0;
            long compressed_size_accumulator = 0;
            long decompressed_size_accumulator = 0;
            /* must be faster: batch read-in strategy */
            byte[] numers = new byte[(int) record_block_info_size];
            data_in1.read(numers);
            data_in1.close();
            long compressed_size, decompressed_size;
            for (int i = 0; i < _num_record_blocks; i++) {
                compressed_size = _version >= 2 ? ByteUtils.toLong(numers, i * 16) : ByteUtils.toInt(numers, i * 8);
                decompressed_size = _version >= 2 ? ByteUtils.toLong(numers, i * 16 + 8) : ByteUtils.toInt(numers, i * 8 + 4);
                maxComRecSize = Math.max(maxComRecSize, compressed_size);
                maxDecompressedSize = Math.max(maxDecompressedSize, decompressed_size);

                recordInfo_list[i] = new RecordInfo(compressed_size, compressed_size_accumulator, decompressed_size, decompressed_size_accumulator);
                compressed_size_accumulator += compressed_size;
                decompressed_size_accumulator += decompressed_size;
            }
            //assert(size_counter == record_block_info_size);
            _recordInfo_list = recordInfo_list;
        }

        long _read_number(ByteBuffer sf) {
            if (_number_width == 4)
                return sf.getInt();
            else
                return sf.getLong();
        }

        long _read_number(DataInputStream sf) throws IOException {
            if (_number_width == 4)
                return sf.readInt();
            else
                return sf.readLong();
        }

        public static byte[] zlib_decompress(byte[] encdata, int offset) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InflaterOutputStream inf = new InflaterOutputStream(out);
                inf.write(encdata, offset, encdata.length - offset);
                inf.close();
                return out.toByteArray();
            } catch (Exception ex) {
                ex.printStackTrace();
                return "ERR".getBytes();
            }
        }

        public static byte[] lzo_decompress(byte[] compressed, int offset) {
            return Compression.lzo.decompress(Arrays.copyOfRange(compressed, offset, compressed.length));
        }


        public int findRecordBlockByKeyOff(long keyOffset, int start, int end) {//return rec blck ID
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                return keyOffset >= _recordInfo_list[start + len - 1].decompressed_size_accumulator + _recordInfo_list[start + len - 1].decompressed_size//注意要抛弃 == 项
                        ? findRecordBlockByKeyOff(keyOffset, start + len, end)
                        : findRecordBlockByKeyOff(keyOffset, start, start + len);
            } else {
                return start;
            }
        }

        static class cached_rec_block {
            byte[] record_block_;
            int blockOff;
            //int blockLen;
            int blockID = -100;
        }

        private volatile cached_rec_block RinfoI_cache_ = new cached_rec_block();

        //存储一组RecordBlock
        cached_rec_block prepareRecordBlock(RecordInfo RinfoI, int Rinfo_id) throws IOException {
            if (RinfoI_cache_.blockID == Rinfo_id)
                return RinfoI_cache_;

            if (RinfoI == null)
                RinfoI = _recordInfo_list[Rinfo_id];

            DataInputStream data_in = getStreamAt(_record_block_offset + _number_width * 4 + _num_record_blocks * 2 * _number_width +
                                                  RinfoI.compressed_size_accumulator, false);

            int compressed_size = (int) RinfoI.compressed_size;
            int decompressed_size = rec_decompressed_size = (int) RinfoI.decompressed_size;//用于验证

            byte[] record_block_compressed = new byte[compressed_size];

            int val = data_in.read(record_block_compressed);
            data_in.close();

            if (val < 0)
                throw new IOException("EOF reached preparing rec #" + Rinfo_id + "  for " + this);
            // 4 bytes indicates block compression type
            //BU.printBytes(record_block_compressed,0,4);

            // 4 bytes adler checksum of uncompressed content
            //ByteBuffer sf1 = ByteBuffer.wrap(record_block_compressed);
            //int adler32 = sf1.order(ByteOrder.BIG_ENDIAN).getInt(4);

            cached_rec_block RinfoI_cache = new cached_rec_block();
            RinfoI_cache.blockID = Rinfo_id;
            RinfoI_cache.blockOff = 0;
            val = record_block_compressed[0] | record_block_compressed[1] << 8 | record_block_compressed[2] << 16 | record_block_compressed[3] << 32;
            //解压开始
            switch (val) {
                default:
                case 0://no compression
                    RinfoI_cache.record_block_ = record_block_compressed;
                    RinfoI_cache.blockOff = 8;
                    //com.knziha.plod.CMN.Log(_key_block_compressed.length,start,8);
                    //System.arraycopy(_key_block_compressed, (+8), key_block, 0,key_block.length);
                    break;
                case 1:
                    RinfoI_cache.record_block_ = new byte[decompressed_size];
                    Compression.Lzo.decompress2(record_block_compressed, 8, compressed_size - 8, RinfoI_cache.record_block_);
//                    new Compression.Lzo.Decompressor1x()
//                            .decompress(record_block_compressed, 8, compressed_size - 8, RinfoI_cache.record_block_, 0, new Compression.Lzo.Len());
                    break;
                case 2:
                    RinfoI_cache.record_block_ = new byte[decompressed_size];
                    //key_block = zlib_decompress(_key_block_compressed,(int) (start+8),(int)(compressedSize-8));
                    Inflater inf = new Inflater();
                    inf.setInput(record_block_compressed, +8, compressed_size - 8);
                    try {
                        int ret = inf.inflate(RinfoI_cache.record_block_, 0, decompressed_size);
                    } catch (DataFormatException e) {
                        //SU.Log(e);
                    }
                    break;
            }
    
    
            /* //adler32 return signed value
            assert(adler32 == (BU.calcChecksum(record_block,0,decompressed_size) ));
            assert(record_block.length == decompressed_size );*/
            RinfoI_cache_ = RinfoI_cache;
            return RinfoI_cache;
        }


        //for listview
        public String getEntryAt(int position) {
            if (position == -1) return "about:";
            if (_key_block_info_list == null) readKeyBlockInfo(null);
            int blockId = accumulation_blockId_tree.xxing(new myCpr<>(position, 1)).getKey().value;
            KeyInfo infoI = _key_block_info_list[blockId];
            return new String(prepareItemByKeyInfo(infoI, blockId, null).keys[(int) (position - infoI.num_entries_accumulator)], _charset);
        }


        protected void getRecordData(int position, RecordLogicLayer retriever) throws IOException {
            if (position < 0 || position >= _num_entries) return;
            if (_recordInfo_list == null) decode_record_block_header();
            int blockId = accumulation_blockId_tree.xxing(new myCpr<>(position, 1)).getKey().value;
            KeyInfo infoI = _key_block_info_list[blockId];
            cached_key_block infoI_cache = prepareItemByKeyInfo(infoI, blockId, null);

            int i = (int) (position - infoI.num_entries_accumulator);
            Integer Rinfo_id = findRecordBlockByKeyOff(infoI_cache.key_offsets[i], 0, _recordInfo_list.length);//accumulation_RecordB_tree.xxing(new mdictRes.myCpr(,1)).getKey().value;//null 过 key前
            RecordInfo RinfoI = _recordInfo_list[Rinfo_id];

            cached_rec_block RinfoI_cache = prepareRecordBlock(RinfoI, Rinfo_id);

            // split record block according to the offset info from key block
            long record_start = infoI_cache.key_offsets[i] - RinfoI.decompressed_size_accumulator;
            long record_end;
            if (i < infoI.num_entries - 1) {
                record_end = infoI_cache.key_offsets[i + 1] - RinfoI.decompressed_size_accumulator;
            } else {
                if (blockId + 1 < _key_block_info_list.length) {
                    //TODO construct a margin checker
                    record_end = prepareItemByKeyInfo(null, blockId + 1, null).key_offsets[0] - RinfoI.decompressed_size_accumulator;
                } else
                    record_end = rec_decompressed_size;
            }
            retriever.ral = (int) record_start + RinfoI_cache.blockOff;
            retriever.val = (int) record_end + RinfoI_cache.blockOff;
            /* May have resource reroute target */
            if (compareByteArrayIsPara(RinfoI_cache.record_block_, retriever.ral, linkRenderByt)) {
                int length = (int) (record_end - record_start - linkRenderByt.length);
                if (length > 0) {
                    String rT = new String(RinfoI_cache.record_block_, retriever.ral + linkRenderByt.length, length, StandardCharsets.UTF_16LE).trim();
                    //com.knziha.plod.CMN.Log("重定向", rT);
                    int np = lookUp(rT, true);
                    if (np >= 0 && np != position) {
                        getRecordData(np, retriever);
                        return;
                    }
                }
            }
            retriever.data = RinfoI_cache.record_block_;
        }

        //    public byte[] getRecordData(int position) throws IOException {
        //        RecordLogicLayer va1 = new RecordLogicLayer();
        //        getRecordData(position, va1);
        //        byte[] data = va1.data;
        //        int record_start = va1.ral;
        //        int record_end = va1.val;
        //
        //        byte[] record = new byte[(record_end - record_start)];
        //        int recordLen = record.length;
        //        if (recordLen + record_start > data.length)
        //            recordLen = data.length - record_start;
        //
        //        System.arraycopy(data, record_start, record, 0, recordLen);
        //        return record;
        //    }

        //    public InputStream getRecordDataStream(int position) throws IOException {
        //        long time = System.currentTimeMillis();
        //        RecordLogicLayer va1 = new RecordLogicLayer();
        //        getRecordData(position, va1);
        //        StringUtils.Log("getRecordDataStream_time", System.currentTimeMillis() - time);
        //        int record_start = va1.ral;
        //        return new ByteArrayInputStream(va1.data, record_start, va1.val - record_start);
        //    }

        public String decodeRecordData(int position, Charset charset) {
            RecordLogicLayer layer = new RecordLogicLayer();
            try {
                getRecordData(position, layer);
            } catch (IOException e) {
            }
            if (layer.data != null)
                return new String(layer.data, layer.ral, layer.val - layer.ral, charset);
            return "";
        }

        public ByteArrayInputStream getResourseAt(int position) throws IOException {
            RecordLogicLayer va1 = new RecordLogicLayer();
            getRecordData(position, va1);

            return new ByteArrayInputStream(va1.data, va1.ral, Math.min(va1.val - va1.ral, va1.data.length - va1.ral));
        }

        static class cached_key_block {
            byte[][] keys;
            long[] key_offsets;
            byte[] hearderText = null;
            byte[] tailerKeyText = null;
            String hearderTextStr = null;
            String tailerKeyTextStr = null;
            int blockID = -100;
        }

        private cached_key_block infoI_cache_ = new cached_key_block();

        public cached_key_block prepareItemByKeyInfo(KeyInfo infoI, int blockId, cached_key_block infoI_cache) {
            cached_key_block infoI_cache_ = this.infoI_cache_;
            if (_key_block_info_list == null) readKeyBlockInfo(null);
            if (infoI_cache_.blockID == blockId)
                return infoI_cache_;
            if (infoI_cache == null)
                infoI_cache = new cached_key_block();
            try {
                if (infoI == null)
                    infoI = _key_block_info_list[blockId];
                infoI_cache.keys = new byte[(int) infoI.num_entries][];
                infoI_cache.key_offsets = new long[(int) infoI.num_entries];
                infoI_cache.hearderText = infoI.headerKeyText;
                infoI_cache.tailerKeyText = infoI.tailerKeyText;
                long start = infoI.key_block_compressed_size_accumulator;
                long compressedSize;

                if (blockId == _key_block_info_list.length - 1)
                    compressedSize = _key_block_size - _key_block_info_list[_key_block_info_list.length - 1].key_block_compressed_size_accumulator;
                else
                    compressedSize = _key_block_info_list[blockId + 1].key_block_compressed_size_accumulator - infoI.key_block_compressed_size_accumulator;

                DataInputStream data_in = getStreamAt(_key_block_offset + start, false);

                byte[] _key_block_compressed = new byte[(int) compressedSize];
                data_in.read(_key_block_compressed, 0, _key_block_compressed.length);
                data_in.close();

                //int adler32 = getInt(_key_block_compressed[+4],_key_block_compressed[+5],_key_block_compressed[+6],_key_block_compressed[+7]);

                byte[] key_block;
                int BlockOff = 0;
                int BlockLen = (int) infoI.key_block_decompressed_size;
                //解压开始
                switch (_key_block_compressed[0] | _key_block_compressed[1] << 8 | _key_block_compressed[2] << 16 | _key_block_compressed[3] << 32) {
                    default:
                    case 0://no compression
                        key_block = _key_block_compressed;
                        BlockOff = 8;
                        break;
                    case 1:
                        key_block = new byte[BlockLen];
                        Compression.Lzo.decompress2(_key_block_compressed, 8, (int) (compressedSize - 8), key_block);
//                        new Compression.Lzo.Decompressor1x()
//                                .decompress(_key_block_compressed, 8, (int) (compressedSize - 8), key_block, 0, new Compression.Lzo.Len());
                        break;
                    case 2:
                        key_block = new byte[BlockLen];
                        //key_block = zlib_decompress(_key_block_compressed,(int) (start+8),(int)(compressedSize-8));
                        Inflater inf = new Inflater();
                        inf.setInput(_key_block_compressed, +8, (int) (compressedSize - 8));
                        try {
                            int ret = inf.inflate(key_block, 0, (int) (infoI.key_block_decompressed_size));
                        } catch (DataFormatException e) {
                            e.printStackTrace();
                        }
                        break;
                }
                /*spliting current Key block*/
                int key_start_index = 0,
                        key_end_index,
                        keyCounter = 0;

                while (key_start_index < BlockLen && keyCounter < infoI.num_entries) {// 莫须有小于
                    long key_id = _version < 2 ? ByteUtils.toInt(key_block, BlockOff + key_start_index)
                            : ByteUtils.toLong(key_block, BlockOff + key_start_index);

                    key_end_index = key_start_index + _number_width;
                    SK_DELI:
                    while (key_end_index + delimiter_width < BlockLen) {
                        for (int sker = 0; sker < delimiter_width; sker++) {
                            if (key_block[BlockOff + key_end_index + sker] != 0) {
                                key_end_index += delimiter_width;
                                continue SK_DELI;
                            }
                        }
                        break;//all match
                    }

                    //SU.Log("key_start_index", key_start_index);
                    byte[] arraytmp = new byte[key_end_index - (key_start_index + _number_width)];
                    System.arraycopy(key_block, BlockOff + key_start_index + _number_width, arraytmp, 0, arraytmp.length);


                    //SU.Log(keyCounter,":::",new String(arraytmp, _charset));
                    key_start_index = key_end_index + delimiter_width;
                    //SU.Log(infoI_cache.keys.length+"~~~"+keyCounter+"~~~"+infoI.num_entries);
                    infoI_cache.keys[keyCounter] = arraytmp;

                    infoI_cache.key_offsets[keyCounter] = key_id;
                    keyCounter++;
                }
                //long end2=System.currentTimeMillis(); //获取开始时间
                //System.out.println("解压耗时："+(end2-start2));
                //assert(adler32 == (calcChecksum(key_block)));
                infoI_cache.blockID = blockId;
                this.infoI_cache_ = infoI_cache;
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            return infoI_cache;

        }

        @Deprecated
        public void findAllKeys(String keyword) {
            keyword = MdxFile.processText(keyword);
            int blockCounter = 0;
            for (KeyInfo infoI : _key_block_info_list) {
                prepareItemByKeyInfo(infoI, blockCounter, null);
                for (byte[] entry : infoI_cache_.keys) {
                    String kk = new String(entry);
                    if (kk.contains(keyword))
                        Log(kk);
                }
                blockCounter++;
            }
        }


        protected HashMap<String, String> _header_tag;

        static int getInt(byte buf1, byte buf2, byte buf3, byte buf4) {
            int r = 0;
            r |= (buf1 & 0xff);
            r <<= 8;
            r |= (buf2 & 0xff);
            r <<= 8;
            r |= (buf3 & 0xff);
            r <<= 8;
            r |= (buf4 & 0xff);
            return r;
        }

        //per-byte byte array comparing
        static int compareByteArray(byte[] A, byte[] B) {
            int la = A.length, lb = B.length;
            int lc = Math.min(la, lb);
            for (int i = 0; i < lc; i++) {
                int cpr = (A[i] & 0xff) - (B[i] & 0xff);
                if (cpr != 0) {
                    return cpr;
                }
            }
            if (la == lb) {
                return 0;
            }
            return la > lb ? 1 : -1;
        }

        static boolean compareByteArrayIsPara(byte[] A, int offA, byte[] B) {
            if (offA + B.length > A.length)
                return false;
            for (int i = 0; i < B.length; i++) {
                if (A[offA + i] != B[i])
                    return false;
            }
            return true;
        }

        public String getPath() {
            return file.getPath();
        }


        public int reduce(String phrase, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                //SU.Log(new String(_key_block_info_list[start].tailerKeyText,_charset)+"  "+new String(_key_block_info_list[start + len - 1].tailerKeyText,_charset));
                return phrase.compareTo(new String(_key_block_info_list[start + len - 1].tailerKeyText, _charset).toLowerCase()) > 0
                        ? reduce(phrase, start + len, end)
                        : reduce(phrase, start, start + len);
            } else {
                return start;
            }
        }

        public int lookUp(String keyword) {
            return lookUp(keyword, true);
        }

        public int lookUp(String keyword, boolean isSrict) {
            if (_key_block_info_list == null) readKeyBlockInfo(null);
            //keyword = mdict.processText(keyword);
            keyword = keyword.toLowerCase();

            int blockId = reduce(keyword, 0, _key_block_info_list.length);
            //show("blockId:"+blockId);
            //while(blockId!=0 &&  compareByteArray(_key_block_info_list[blockId-1].tailerKeyText,keyword.getBytes(_charset))>=0)
            //	blockId--;

            KeyInfo infoI = _key_block_info_list[blockId];

            cached_key_block infoI_cache = prepareItemByKeyInfo(infoI, blockId, null);

            int res = reduce_keys_raw(infoI_cache.keys, keyword, 0, infoI_cache.keys.length);//keyword
            //SU.Log("search failed!asdasd", res);
            if (res == -1) {
                //SU.Log("search failed!");
                return -1;
            } else {
                if (isSrict && !new String(infoI_cache.keys[res], _charset).toLowerCase().equals(keyword))
                    return -1;
                //String KeyText= infoI_cache.keys[res];
                //long wjOffset = infoI.key_block_compressed_size_accumulator+infoI_cache.key_offsets[res];
                return (int) (infoI.num_entries_accumulator + res);
            }
        }


        //	public ByteArrayInputStream getResourseByKey(String key) throws IOException {
        //		int idx = lookUp(key);
        //		if(idx>=0){
        //			RecordLogicLayer va1=new RecordLogicLayer();
        //			getRecordData(idx, va1);
        //			return new BSI(va1.data, va1.ral, va1.val-va1.ral);
        //		}
        //		return null;
        //	}

        public int reduce_keys_raw(byte[][] keys, String val, int start, int end) {//via mdict-js
            int len = end - start;
            //SU.Log(new String(keys[start],_charset)+"  "+new String(keys[Math.min(end, keys.length-1)],_charset));
            if (len > 1) {
                len = len >> 1;
                return val.compareTo(new String(keys[start + len - 1], _charset).toLowerCase()) > 0
                        ? reduce_keys_raw(keys, val, start + len, end)
                        : reduce_keys_raw(keys, val, start, start + len);
            } else {
                return start;
            }
        }

        @Override
        public String toString() {
            return super.toString() + "::" + file.getName();
        }

        public static String processText(CharSequence input) {
            return replaceReg.matcher(input).replaceAll(emptyStr).toLowerCase();
        }

        static class F1ag {
            public int val;
        }

        static class Flag extends F1ag {
            public String data;
        }

        static class RecordLogicLayer extends F1ag {
            public int ral;
            public byte[] data;
        }

        /*store key_block's summary*/
        static class KeyInfo {

            public KeyInfo(long num_entries_, long num_entries_accumulator_) {
                num_entries = num_entries_;
                num_entries_accumulator = num_entries_accumulator_;
            }

            public byte[] headerKeyText;
            public byte[] tailerKeyText;
            public long key_block_compressed_size_accumulator;
            public long key_block_compressed_size;
            public long key_block_decompressed_size;
            public long num_entries;
            public long num_entries_accumulator;
            //public String[] keys;
            //public long[] key_offsets;
            //public byte[] key_block_data;
        }

        static class RecordInfo {
            public RecordInfo(long _compressed_size, long _compressed_size_accumulator, long _decompressed_size, long _decompressed_size_accumulator) {
                compressed_size = _compressed_size;
                compressed_size_accumulator = _compressed_size_accumulator;
                decompressed_size = _decompressed_size;
                decompressed_size_accumulator = _decompressed_size_accumulator;

            }

            public long compressed_size;
            public long compressed_size_accumulator;
            public long decompressed_size;
            public long decompressed_size_accumulator;
        }
    }

    /**
     * **Mdict Java Library**<br/><br/>
     * <b>FEATURES</b>:<br/>
     * 1. Basic listing and fast binary query of mdx files.<br/>
     * 2. Dictionary conjunction search.<br/>
     * 3. Fast Multi-threaded search in all contents.<br/>
     * 4. Fast Multi-threaded search in all entries.<br/>
     * 5. Optional regex expression engine( Joni ) or wildcards( .* ) for above two search modes.<br/><br/>
     * Author : KnIfER<br/>
     * <b>Licence</b> : Apache2.0 under this package (com.knziha.plod.dictionary.*); GPL3.0 for everything else including the mdictBuilder. <br/>
     */
    public static class MdxFile extends MdbFile {
        private MdxFile parent;
        byte[] textLineBreak;

        /**
         * Packed mdd files.
         */
        protected List<MddFile> mdd;
        /**
         * Unpacked file tree.
         */
        protected List<File> ftd;

        protected MdxFile virtualIndex;

        public String _Dictionary_fName;
        public /*final*/ boolean isResourceFile;
        private EncodeChecker encodeChecker;
        private HashMap<Integer, String> PageNumberMap;
        protected File fZero;
        private long fZero_LPT;

        //public int KeycaseStrategy=0;//0:global 1:Java API 2:classical
        public int getCaseStrategy() {
            return 0;
        }

        public static boolean bGlobalUseClassicalKeycase = false;

        public boolean isGBoldCodec;

        public String currentDisplaying;

        public volatile boolean searchCancled;

        /**
         * validation schema<br/>
         * 0=none; 1=check even; 2=check four; 3=check direct; 4=check direct for all; 5=1/3;
         */
        protected int checkEven;
        protected int maxEB;

        public byte[] htmlOpenTag;
        public byte[] htmlCloseTag;
        public byte[][] htmlTags;
        public byte[][] htmlTagsA;
        public byte[][] htmlTagsB;

        //构造
        public MdxFile(File file) throws IOException {
            this(file, 0, null, null);
        }

        //构造
        public MdxFile(File file, int pseudoInit, StringBuilder buffer, Object tag) throws IOException {
            super(file, pseudoInit, buffer, tag);
            if (pseudoInit == 1) {
                _Dictionary_fName = this.file.getName();
            }
        }

        protected MdxFile(MdxFile master, DataInputStream data_in, long _ReadOffset) throws IOException {
            super(master, data_in);
            parent = master;
            ReadOffset = _ReadOffset;
            isResourceFile = false;
        }

        @Override
        protected void init(DataInputStream data_in) throws IOException {
            super.init(data_in);
            _Dictionary_fName = file.getName();
            textLineBreak = lineBreakText.getBytes(_charset);
            // ![0] load options
            ScanSettings();
            // ![1] load mdds
            loadInResourcesFiles(null);
            calcFuzzySpace();
            if (_header_tag.containsKey("hasSlavery")) {
                try {
                    long skip = data_in.skipBytes((int) _key_block_size);
                    decodeRecordBlockSize(data_in);
                    int toTail = (int) (_record_block_size + _record_block_info_size);
                    //SU.Log("Slavery.Init ...", skip, _key_block_size, ReadOffset, _record_block_offset, _version, toTail);
                    skip += data_in.skipBytes(toTail);
                    if (skip == _key_block_size + toTail && data_in.available() > 0) {
                        virtualIndex = new MdxFile(this, data_in, ReadOffset + _record_block_offset + (_version >= 2 ? 32 : 16) + toTail);
                        Log("Slavery.Init OK");
                    }
                } catch (IOException e) {
                    Log("Slavery.Init Error");
                    Log(e);
                }
            }
            data_in.close();
            isGBoldCodec = _encoding.startsWith("GB") && !_header_tag.containsKey("PLOD");
        }

        public InputStream getResourceByKey(String key) {
            Log("getResourceByKey", _Dictionary_fName, ftd);
            if (ftd != null && ftd.size() > 0) {
                String keykey = key.replace("\\", File.separator);
                for (File froot : ftd) {
                    File ft = new File(froot, keykey);
                    Log("getResourceByKey", _Dictionary_fName, ft.getAbsolutePath(), ft.exists());
                    if (ft.exists()) {
                        try {
                            return new FileInputStream(ft);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (isResourceFile) {
                int idx = lookUp(key);
                if (idx >= 0) {
                    try {
                        return getResourseAt(idx);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (mdd != null && mdd.size() > 0) {
                for (MddFile mddTmp : mdd) {
                    int idx = mddTmp.lookUp(key);
                    if (idx >= 0) {
                        try {
                            return mddTmp.getResourseAt(idx);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    //else SU.Log("chrochro inter_ key is not find:",_Dictionary_fName,key, idx);
                }
            }
            return null;
        }

        protected void ScanSettings() {

        }

        public String getCachedEntryAt(int pos) {
            return currentDisplaying;
        }

        @Override
        public long getNumberEntries() {
            if (virtualIndex != null)
                return virtualIndex._num_entries;
            return _num_entries;
        }

        //for lv
        public String getEntryAt(int position, Flag mflag) {
            if (virtualIndex != null)
                return virtualIndex.getEntryAt(position, mflag);
            if (position == -1) return "about:";
            if (_key_block_info_list == null) readKeyBlockInfo(null);
            int blockId = accumulation_blockId_tree.xxing(new myCpr<>(position, 1)).getKey().value;
            KeyInfo infoI = _key_block_info_list[blockId];
            if (compareByteArray(infoI.headerKeyText, infoI.tailerKeyText) == 0)
                mflag.data = new String(infoI.headerKeyText, _charset);
            else
                mflag.data = null;
            //TODO null pointer error
            return new String(prepareItemByKeyInfo(infoI, blockId, null).keys[(int) (position - infoI.num_entries_accumulator)], _charset);
        }

        @Override
        public String getEntryAt(int position) {
            if (virtualIndex != null)
                return virtualIndex.getEntryAt(position);
            return super.getEntryAt(position);
        }

        public int reduce_index2(byte[] phrase, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                //show("reducearound:"+(start + len - 1)+"@"+len+": "+new String(_key_block_info_list[start + len - 1].tailerKeyText));
                //show(start+"::"+end+"   "+new String(_key_block_info_list[start].tailerKeyText,_charset)+"::"+new String(_key_block_info_list[end==_key_block_info_list.length?end-1:end].tailerKeyText,_charset));
                byte[] zhujio = _key_block_info_list[start + len - 1].tailerKeyText;
                return compareByteArray(phrase, /*isCompact*/false ? zhujio : processMyText(new String(zhujio, _charset)).getBytes(_charset)) > 0
                        ? reduce_index2(phrase, start + len, end)
                        : reduce_index2(phrase, start, start + len);
            } else {
                return start;
            }
        }

        public int reduce_index(String phrase, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                //show("reducearound:"+(start + len - 1)+"@"+len+": "+new String(_key_block_info_list[start + len - 1].tailerKeyText));
                //show(start+"::"+end+"   "+new String(_key_block_info_list[start].tailerKeyText,_charset)+"::"+new String(_key_block_info_list[end==_key_block_info_list.length?end-1:end].tailerKeyText,_charset));
                String zhujio = new String(_key_block_info_list[start + len - 1].tailerKeyText, _charset);
                return phrase.compareToIgnoreCase(/*isCompact*/false ? zhujio : processMyText(zhujio)) > 0
                        ? reduce_index(phrase, start + len, end)
                        : reduce_index(phrase, start, start + len);
            } else {
                return start;
            }
        }

        public int lookUp(String keyword) {
            return lookUp(keyword, false);
        }

        String HeaderTextStr;

        public int lookUp(String keyword, boolean isSrict) {
            if (isResourceFile) {
                if (!keyword.startsWith("\\"))
                    keyword = "\\" + keyword;
                return super.lookUp(keyword, isSrict);
            }
            if (virtualIndex != null) {
                return virtualIndex.lookUp(keyword, isSrict);
            }
            if (_key_block_info_list == null) readKeyBlockInfo(null);
            String keyOrg = keyword;
            keyword = processMyText(keyword);
            byte[] kAB = keyword.getBytes(_charset);

            int blockId = -1;

            //isGBoldCodec = true;

            if (isGBoldCodec) {
                int boudaryCheck = compareByteArray(_key_block_info_list[(int) _num_key_blocks - 1].tailerKeyText, kAB);
                if (boudaryCheck < 0)
                    return -1;
                if (boudaryCheck == 0) blockId = (int) _num_key_blocks - 1;
                boudaryCheck = compareByteArray(_key_block_info_list[0].headerKeyText, kAB);
                if (boudaryCheck > 0)
                    return -1;
                if (boudaryCheck == 0) return 0;
            } else {
                int boudaryCheck = processMyText(new String(_key_block_info_list[(int) _num_key_blocks - 1].tailerKeyText, _charset)).compareTo(keyword);
                if (boudaryCheck < 0)
                    return -1;
                if (boudaryCheck == 0) blockId = (int) _num_key_blocks - 1;
                if (HeaderTextStr == null)
                    HeaderTextStr = processMyText(new String(_key_block_info_list[0].headerKeyText, _charset));
                boudaryCheck = HeaderTextStr.compareTo(keyword);
                if (boudaryCheck > 0) {
                    if (HeaderTextStr.startsWith(keyword)) {
                        return isSrict ? -(0 + 2) : 0;
                    } else
                        return -1;
                }
                if (boudaryCheck == 0) return 0;
            }
            if (blockId == -1)
                blockId = isGBoldCodec ? reduce_index2(keyword.getBytes(_charset), 0, _key_block_info_list.length) : reduce_index(keyword, 0, _key_block_info_list.length);
            if (blockId == -1) return blockId;

            //SU.Log("blockId:",blockId, new String(_key_block_info_list[blockId].headerKeyText,_charset), new String(_key_block_info_list[blockId].tailerKeyText,_charset));
            //while(blockId!=0 &&  compareByteArray(_key_block_info_list[blockId-1].tailerKeyText,kAB)>=0) blockId--;
            //SU.Log("finally blockId is:"+blockId+":"+_key_block_info_list.length);


            KeyInfo infoI = _key_block_info_list[blockId];

            //smart shunt
            if (compareByteArray(infoI.headerKeyText, infoI.tailerKeyText) == 0) {
                if (isSrict)
                    return -1 * (int) ((infoI.num_entries_accumulator + 2));
                else
                    return (int) infoI.num_entries_accumulator;
            }

            cached_key_block infoI_cache = prepareItemByKeyInfo(infoI, blockId, null);

            int res;
            if (isGBoldCodec)
                //res = binary_find_closest2(infoI_cache.keys,keyword);//keyword
                res = reduce_keys2(infoI_cache.keys, kAB, 0, infoI_cache.keys.length);
            else
                //res = binary_find_closest(infoI_cache.keys,keyword);//keyword
                res = reduce_keys(infoI_cache.keys, keyword, 0, infoI_cache.keys.length);

            if (res == -1) {
                System.out.println("search failed!" + keyword);
                return -1;
            }
            //SU.Log(keyword, res, getEntryAt((int) (res+infoI.num_entries_accumulator)));
            ////if(isCompact) //compatibility fix
            String other_key = new String(infoI_cache.keys[res], _charset);
            String looseMatch = processMyText(other_key);
            boolean bIsEqual = looseMatch.equals(keyword);

            if (!bIsEqual) {
                boolean b2 = false;
                Matcher m = null;
                if (other_key.endsWith(">") && (keyOrg.endsWith(">") || (b2 = (m = numSuffixedReg.matcher(keyOrg)).find()))) {
                    /* possible to be number-suffixed */
                    int idx2 = b2 ? m.start(1) : keyOrg.lastIndexOf("<");
                    if (idx2 > 0 && idx2 == other_key.lastIndexOf("<")) {
                        int start = parseint(other_key.substring(idx2 + 1, other_key.length() - 1));
                        int target = b2 ? IntUtils.parsint(m.group(1))
                                : parseint(keyOrg.substring(idx2 + 1, keyOrg.length() - 1));
                        //com.knziha.plod.CMN.Log(keyOrg,other_key,start,target);
                        int PstPosition = (int) (infoI.num_entries_accumulator + res + (target - start));
                        String other_other_key = getEntryAt(PstPosition);
                        if (other_other_key.length() > idx2 && other_other_key.endsWith(">") && other_other_key.charAt(idx2) == '<') {
                            /* match end key's number */
                            //if(keyOrg.startsWith(other_other_key.substring(0, idx2))){
                            if (keyOrg.regionMatches(true, 0, other_other_key, 0, idx2)) {
                                int end = parseint(other_other_key.substring(idx2 + 1, other_other_key.length() - 1));
                                if (target == end) {
                                    Log("target==end", getEntryAt(PstPosition));
                                    return PstPosition;
                                }
                            }
                        }
                    }
                }

                if (isSrict) {
                    Log(getEntryAt((int) (infoI.num_entries_accumulator + res)), res, "::", -1 * (res + 2));
                    return -1 * (int) ((infoI.num_entries_accumulator + res + 2));
                }
            }

            //String KeyText= infoI_cache.keys[res];
            //for(String ki:infoI.keys) SU.Log(ki);
            //show("match key "+KeyText+" at "+res);
            return (int) (infoI.num_entries_accumulator + res);
        }

        private int parseint(String item) {
            if (IntUtils.shuzi.matcher(item).find())
                return IntUtils.parsint(item);
            else if (IntUtils.hanshuzi.matcher(item).find())
                return IntUtils.recurse1wCalc(item, 0, item.length() - 1, 1);
            return -1;
        }

        private int try_get_tailing_number(String keyOrg) {
            if (keyOrg.endsWith(">")) {
                int idx2 = keyOrg.lastIndexOf("<", keyOrg.length() - 2);
                if (idx2 != -1) {
                    String item = keyOrg.substring(idx2 + 1, keyOrg.length() - 1);
                    if (IntUtils.hanshuzi.matcher(item).find())
                        return IntUtils.recurse1wCalc(item, 0, item.length() - 1, 1);
                    else if (IntUtils.shuzi.matcher(item).find())
                        return IntUtils.parsint(item);
                }
            } else {
                Matcher m = numSuffixedReg.matcher(keyOrg);
                if (m.find()) {
                    return IntUtils.parsint(m.group());
                }
            }
            return -1;
        }

        public int reduce_keys(byte[][] keys, String val, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                //String zhujue = processMyText(new String(keys[start + len - 1],_charset));
              /*if(!isCompact) {//fixing python writemdict compatibility
                  if(infoI_cache.hearderTextStr==null) {
                      infoI_cache.hearderTextStr=new String(infoI_cache.hearderText,_charset);
                      infoI_cache.tailerKeyTextStr=new String(infoI_cache.tailerKeyText,_charset);
                  }
                  if(infoI_cache.tailerKeyTextStr.compareTo(zhujue)>0 || infoI_cache.hearderTextStr.compareTo(zhujue)<0) {
                      zhujue = replaceReg2.matcher(zhujue).replaceAll(emptyStr);
                  }
              }*/

                //show("->"+new String(keys[start + len - 1],_charset)+" ="+val.compareTo(processMyText(new String(keys[start + len - 1],_charset))));
                //show(start+"::"+end+"   "+new String(keys[start],_charset)+"::"+new String(keys[end==keys.length?end-1:end],_charset));


                return val.compareTo(processMyText(new String(keys[start + len - 1], _charset))) > 0
                        ? reduce_keys(keys, val, start + len, end)
                        : reduce_keys(keys, val, start, start + len);
            } else {
                return start;
            }
        }

        public int reduce_keys2(byte[][] keys, byte[] val, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;
                //String zhujue = processMyText(new String(keys[start + len - 1],_charset));
              /*if(!isCompact) {//fixing python writemdict compatibility
                  if(infoI_cache.hearderTextStr==null) {
                      infoI_cache.hearderTextStr=new String(infoI_cache.hearderText,_charset);
                      infoI_cache.tailerKeyTextStr=new String(infoI_cache.tailerKeyText,_charset);
                  }
                  if(infoI_cache.tailerKeyTextStr.compareTo(zhujue)>0 || infoI_cache.hearderTextStr.compareTo(zhujue)<0) {
                      zhujue = replaceReg2.matcher(zhujue).replaceAll(emptyStr);
                  }
              }*/
                //SU.Log(start+"::"+end+"   "+new String(keys[start],_charset)+"::"+new String(keys[end],_charset));
                return compareByteArray(val, processMyText(new String(keys[start + len - 1], _charset)).getBytes(_charset)) > 0
                        ? reduce_keys2(keys, val, start + len, end)
                        : reduce_keys2(keys, val, start, start + len);
            } else {
                return start;
            }
        }

//        public String getRecordsAt(int... positions) throws IOException {
//            if (isResourceFile)
//                return constructLogicalPage(positions);
//            String ret;
//            int p0 = positions[0];
//            if (positions.length == 1) {
//                ret = getRecordAt(p0);
//            } else {
//                StringBuilder sb = new StringBuilder();
//                int c = 0;
//                for (int i : positions) {
//                    sb.append(getRecordAt(i));//.trim()
//                    if (c != positions.length - 1)
//                        sb.append("<HR>");
//                    c++;
//                }
//                ret = sb.toString();
//            }
//            return processStyleSheet(ret, p0);
//        }

        //    /**
        //     * @param positions virutal indexes
        //     */
        //    public String getVirtualRecordsAt(int... positions) throws IOException {
        //        if (virtualIndex == null)
        //            return getRecordsAt(positions);
        //        StringBuilder sb = new StringBuilder();
        //        int c = 0, lastAI = -1;
        //        for (int i : positions) {
        //            String vi = virtualIndex.getRecordAt(i);
        //            JSONObject vc = JSONObject.parseObject(vi);
        //            int AI = vc.getInteger("I");
        //            if (lastAI == AI) {
        //                //TODO overlaping case
        //            } else {
        //                String JS = vc.getString("JS");
        //                String record = getRecordAt(AI);
        //                int headId = record.indexOf("<head>");
        //                if (headId < 0) {
        //                    headId = -6;
        //                    sb.append("<head>");
        //                }
        //                sb.append(record, 0, headId + 6);
        //                sb.append("<script>");
        //                sb.append(JS == null ? "" : JS);
        //                sb.append("</script>");
        //                if (headId < 0) sb.append("<head>");
        //                sb.append(record, headId + 6, record.length());
        //            }
        //            lastAI = AI;
        //
        //            if (c != positions.length - 1)
        //                sb.append("<HR>");
        //            c++;
        //        }
        //        sb.append("<div class=\"_PDict\" style='display:none;'><p class='bd_body'/>");
        //        if (mdd != null && mdd.size() > 0) sb.append("<p class='MddExist'/>");
        //        sb.append("</div>");
        //        return processStyleSheet(sb.toString(), positions[0]);
        //    }

        /**
         * <style>
         * audio {
         * position:absolute;
         * top:32%;
         * width:100%;
         * }
         * h2 {
         * position:absolute;
         * top:1%;
         * width:100%;
         * text-align: center;
         * }
         * </style>
         */
        String logicalPageHeader = "SUBPAGE";

        /**
         * Construct Logical Page For mdd resource file.
         */
//        private String constructLogicalPage(int... positions) {
//            StringBuilder LoPageBuilder = new StringBuilder();
//            LoPageBuilder.append(logicalPageHeader);
//            for (int i : positions) {
//                String key = getEntryAt(i);
//                if (key.startsWith("/") || key.startsWith("\\"))
//                    key = key.substring(1);
//                key = StringEscapeUtils.escapeHtml3(key);
//                if (htmlReg.matcher(key).find()) {
//                    LoPageBuilder.append(decodeRecordData(positions[0], StandardCharsets.UTF_8));
//                } else {
//                    if (imageReg.matcher(key).find()) {
//                        LoPageBuilder.append("<img style='width:100%; height:auto;' src=\"").append(key).append("\"></img>");
//                    } else if (soundReg.matcher(key).find()) {
//                        LoPageBuilder.append("<h2>").append(key).append("</h2>");
//                        LoPageBuilder.append("<audio controls='controls' autoplay='autoplay' src=\"").append(key).append("\"></audio>");
//                        LoPageBuilder.append("<h2 style='top:56%'>").append(key).append("</h2>");
//                    } else if (videoReg.matcher(key).find()) {
//                        LoPageBuilder.append("<video width='320' height='240' controls=\"controls\" src=\"").append(key).append("\"></video>");
//                    }
//                }
//            }
//            LoPageBuilder.append("<div class='bd_body'/>");
//            return LoPageBuilder.toString();
//        }
        public static int offsetByTailing(String token) {
            //calculating relative offset represented by number of tailing '\n'.
            //entrys: abc abc acc TO: abc abc\n acc
            if (token.endsWith("\n")) {
                int first = token.length() - 1;
                while (first - 1 > 0 && token.charAt(first - 1) == '\n') {
                    first--;
                }
                return token.length() - first;
            }
            return 0;
        }

        public String getRecordAt(int position) throws IOException {
            if (ftd != null && ftd.size() > 0 && ReadOffset == 0) {
                File ft;
                for (File f : ftd) {
                    String key = getDebugPageResourceKey(position);
                    ft = new File(f, key);
                    //SU.Log(ft.getAbsolutePath(), ft.exists());
                    if (ft.exists())
                        return ByteUtils.fileToString(ft);
                }
            }
            if (position < 0 || position >= _num_entries)
                return "404 index out of bound";
            RecordLogicLayer va1 = new RecordLogicLayer();
            super.getRecordData(position, va1);
            byte[] data = va1.data;
            int record_start = va1.ral;
            int record_end = va1.val;

            if (textTailed(data, record_end - textLineBreak.length, textLineBreak)) record_end -= textLineBreak.length;

            String tmp = new String(data, record_start, record_end - record_start, _charset);

            if (tmp.startsWith(linkRenderStr)) {
                //SU.Log("rerouting",tmp);
                //SU.Log(tmp.replace("\n", "1"));
                String key = tmp.substring(linkRenderStr.length());
                //todo clean up
                int offset = offsetByTailing(key);
                key = key.trim();
                //Log.e("rerouting offset",""+offset);
                int idx = lookUp(key);
                if (idx != -1) {
                    String looseKey = processMyText(key);
                    int tmpIdx = lookUp(key, false);
                    if (tmpIdx != -1) {
                        String looseMatch = getEntryAt(tmpIdx);
                        while (processMyText(looseMatch).equals(looseKey)) {
                            if (looseMatch.equals(key)) {
                                idx = tmpIdx;
                                break;
                            }
                            if (tmpIdx >= getNumberEntries() - 1)
                                break;
                            looseMatch = getEntryAt(++tmpIdx);
                        }
                    }

                    if (offset > 0) {
                        if (key.equals(getEntryAt(idx + offset)))
                            idx += offset;
                    }
                    tmp = getRecordAt(idx);
                }
            }
            return tmp;
        }

        private String getDebugPageResourceKey(int position) {
            String key = null;
            if (PageNumberMap != null) {
                key = PageNumberMap.get(position);
            }
            if (key == null) {
                key = Integer.toString(position);
            }
            return key;
        }

        @Override
        public String decodeRecordData(int position, Charset charset) {
            if (ftd != null && ftd.size() > 0) {
                for (File froot : ftd) {
                    File ft = new File(froot, "" + position);
                    if (ft.exists()) {
                        try {
                            return new String(ByteUtils.fileToByteArr(ft), charset);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return super.decodeRecordData(position, charset);
        }

        public static boolean textTailed(byte[] data, int off, byte[] textLineBreak) {
            if (off + 2 < data.length) {
                return data[off] == textLineBreak[0] && data[off + 1] == textLineBreak[1] && data[off + 2] == textLineBreak[2];
            }
            return false;
        }

        long[] keyBlocksHeaderTextKeyID;


        public int split_keys_thread_number;
        //public ArrayList<myCpr<String,Integer>>[] combining_search_tree;
        public ArrayList<Integer>[] combining_search_tree2;
        public ArrayList<Integer>[] combining_search_tree_4;

        public String getVirtualRecordAt(int vi) throws IOException {
            return virtualIndex.getRecordAt(vi);
        }

        public String getDictionaryName() {
            return _Dictionary_fName;
        }

        public boolean hasMdd() {
            return mdd != null && mdd.size() > 0 || ftd != null && ftd.size() > 0 || isResourceFile;
        }

        public List<MddFile> getMdd() {
            return mdd;
        }

        public String getAboutHtml() {
            return getAboutString();
        }

        public boolean hasStyleSheets() {
            Log("_stylesheet", _stylesheet.size(), _stylesheet.keySet().size(), _stylesheet.values().size());
            return _stylesheet.size() > 0;
        }

        public int thread_number, step, yuShu;

        public void calcFuzzySpace() {
            //final String fkeyword = keyword.toLowerCase().replaceAll(replaceReg,emptyStr);
            //int entryIdx = 0;
            //show("availableProcessors: "+Runtime.getRuntime().availableProcessors());
            //show("keyBLockN: "+_key_block_info_list.length);
            split_keys_thread_number = _num_key_blocks < 6 ? 1 : (int) (_num_key_blocks / 6);//Runtime.getRuntime().availableProcessors()/2*2+10;
            split_keys_thread_number = split_keys_thread_number > 16 ? 6 : split_keys_thread_number;
            thread_number = Math.min(Runtime.getRuntime().availableProcessors() / 2 * 2 + 2, split_keys_thread_number);


            step = (int) (_num_key_blocks / split_keys_thread_number);
            yuShu = (int) (_num_key_blocks % split_keys_thread_number);

        }

        //        public static int binary_find_closest(long[] array, long val) {
//            int middle;
//            int iLen;
//            if (array == null || (iLen = array.length) < 1) {
//                return -1;
//            }
//            int low = 0, high = iLen - 1;
//            if (iLen == 1) {
//                return 0;
//            }
//            if (val - array[0] <= 0) {
//                return 0;
//            } else if (val - array[iLen - 1] >= 0) {
//                return iLen - 1;
//            }
//            int counter = 0;
//            long cprRes1, cprRes0;
//            while (low < high) {
//                counter += 1;
//                //System.out.println(low+":"+high);
//                middle = (low + high) / 2;
//                cprRes1 = array[middle + 1] - val;
//                cprRes0 = array[middle] - val;
//                if (cprRes0 >= 0) {
//                    high = middle;
//                } else if (cprRes1 <= 0) {
//                    //System.out.println("cprRes1<=0 && cprRes0<0");
//                    //System.out.println(houXuan1);
//                    //System.out.println(houXuan0);
//                    low = middle + 1;
//                } else {
//                    //System.out.println("asd");
//                    high = middle;
//                }
//            }
//            return low;
//        }


        public String getAboutString() {
            return _header_tag.get("Description");
        }

        public String getTitle() {
            return _header_tag.getOrDefault("Title", _Dictionary_fName.replace(".mdx", ""));
        }

        public String getDictInfo() {
            DecimalFormat numbermachine = new DecimalFormat("#.00");
            return new StringBuilder()
                    .append("Engine Version: ").append(_version).append("<BR>")
                    .append("CreationDate: ").append(_header_tag.get("CreationDate")).append("<BR>")
                    .append("Charset &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; : ").append(this._encoding).append("<BR>")
                    .append("Num Entries: ").append(_num_entries).append("<BR>")
                    .append("Num Key Blocks: ").append(_num_key_blocks).append("<BR>")
                    .append("Num Rec Blocks: ").append(decodeRecordBlockSize(null)).append("<BR>")
                    .append("Avg. Key Block: ").append(numbermachine.format(1.0 * _key_block_size / _num_key_blocks / 1024)).append(" kb, ").append(numbermachine.format(1.0 * _num_entries / _num_key_blocks)).append(" items / block").append("<BR>")
                    .append("Avg. Rec Block: ").append(numbermachine.format(1.0 * _record_block_size / _num_record_blocks / 1024)).append(" kb, ").append(numbermachine.format(1.0 * _num_entries / _num_record_blocks)).append(" items / block").append("<BR>")
                    .append("Compact  排序: ").append(isCompact).append("<BR>")
                    .append("StripKey 排序: ").append(isStripKey).append("<BR>")
                    .append("Case Sensitive: ").append(isKeyCaseSensitive).append("<BR>")
                    //.append(mdd==null?"&lt;no assiciated mdRes&gt;":("MdResource count "+mdd.getNumberEntries()+","+mdd._encoding+","+mdd._num_key_blocks+","+mdd._num_record_blocks)).append("<BR>")
                    .append("Internal Name: ").append(_header_tag.get("Title")).append("<BR>")
                    .append("Path: ").append(getPath()).toString();
        }

//        static boolean bingStartWith(byte[] source, int sourceOffset, int sourceCount, byte[] target, int targetOffset, int targetCount, int fromIndex) {
//            if (fromIndex >= sourceCount || targetCount + fromIndex > sourceCount) { // || targetCount+fromIndex>=sourceCount || fromIndex>=sourceCount
//                return false;
//            }
//            //if(targetCount<=-1)
//            //	targetCount=target.length;
//            //if(sourceOffset+targetCount>=source.length)
//            //	return false;
//            int i = sourceOffset + fromIndex;
//            int v1 = i + targetCount - 1, v2 = targetOffset - i;
//            for (; i <= v1 && source[i] == target[i + v2]; i++) ;
//            return i == v1 + 1;
//        }

        public String processMyText(String input) {
            String ret = isStripKey ? replaceReg.matcher(input).replaceAll(emptyStr) : input;
            int KeycaseStrategy = getCaseStrategy();
            return isKeyCaseSensitive ? ret : (((KeycaseStrategy > 0) ? (KeycaseStrategy == 2) : bGlobalUseClassicalKeycase) ? mOldSchoolToLowerCase(ret) : ret.toLowerCase());
        }

        public String mOldSchoolToLowerCase(String input) {
            StringBuilder sb = new StringBuilder(input);
            for (int i = 0; i < sb.length(); i++) {
                if (sb.charAt(i) >= 'A' && sb.charAt(i) <= 'Z')
                    sb.setCharAt(i, (char) (sb.charAt(i) + 32));
            }
            return sb.toString();
        }

//        public String processStyleSheet(String input, int pos) {
//            if (_stylesheet.size() == 0)
//                return input;
//            Matcher m = markerReg.matcher(input);
//            //HashSet<String> Already = new HashSet<>();
//            StringBuilder transcriptor = new StringBuilder();
//            String last = null;
//            int lastEnd = 0;
//            boolean returnRaw = true;
//            while (m.find()) {
//                String now = m.group(1);
//                String[] nowArr = _stylesheet.get(now);
//                if (nowArr == null)
//                    if ("0".equals(now)) {
//                        nowArr = new String[]{getCachedEntryAt(pos), ""};
//                    }
//                if (nowArr == null) {
//                    if (last != null) {
//                        transcriptor.append(last);
//                        last = null;
//                    }
//                    continue;
//                }
//                transcriptor.append(input, lastEnd, m.start());
//                if (last != null) transcriptor.append(last);
//                transcriptor.append(StringEscapeUtils.unescapeHtml3(nowArr[0]));
//                lastEnd = m.end();
//                last = nowArr.length == 2 ? StringEscapeUtils.unescapeHtml3(nowArr[1]) : "";
//                returnRaw = false;
//            }
//            if (returnRaw)
//                return input;
//            else
//                return transcriptor.append(last == null ? "" : last).append(input.substring(lastEnd)).toString();
//        }

        @Override
        public String toString() {
            return _Dictionary_fName + "(" + hashCode() + ")";
        }

//        public void Rebase(File newPath) {
//            if (!f.equals(newPath)) {
//                String OldFName = _Dictionary_fName;
//                f = newPath;
//                _Dictionary_fName = f.getName();
//                HashSet<String> mddCon = new HashSet<>();
//                if (mdd != null) {
//                    for (MddFile md : mdd) {
//                        //todo file access
//                        MoveOrRenameResourceLet(md, OldFName, _Dictionary_fName, newPath);
//                        mddCon.add(md.getPath());
//                    }
//                }
//                try {
//                    loadInResourcesFiles(mddCon);
//                } catch (IOException ignored) {
//                }
//            }
//        }

//        protected void MoveOrRenameResourceLet(MddFile md, String token, String pattern, File newPath) {
//            File f = md.f();
//            String tokee = f().getName();
//            if (tokee.startsWith(token) && tokee.charAt(Math.min(token.length(), tokee.length())) == '.') {
//                String suffix = tokee.substring(token.length());
//                String np = f.getParent();
//                File mnp;
//                if (np != null && np.equals(np = newPath.getParent())) { //重命名
//                    mnp = new File(np, pattern + suffix);
//                } else {
//                    mnp = new File(np, f.getName());
//                }
//                if (mnp != null && f.renameTo(mnp)) {
//                    md.Rebase(mnp);
//                }
//            }
//        }

        protected StringBuilder AcquireStringBuffer(int capacity) {
            StringBuilder sb;
            if (univeral_buffer != null && isMainThread()) {
                sb = univeral_buffer;
                sb.ensureCapacity(capacity);
                sb.setLength(0);
                Log("复用字符串构建器……");
            } else {
                sb = new StringBuilder(capacity);
            }
            return sb;
        }

        protected boolean isMainThread() {
            return false;
        }

        private void loadInResourcesFiles(HashSet<String> mddCon) throws IOException {
            if (!isResourceFile) {
                File p = file.getParentFile();
                if (p != null && _num_record_blocks >= 0) {
                    String full_Dictionary_fName = _Dictionary_fName;
                    StringBuilder sb = AcquireStringBuffer(full_Dictionary_fName.length() + 15);
                    int idx = full_Dictionary_fName.lastIndexOf(".");
                    if (idx != -1) {
                        sb.append(full_Dictionary_fName, 0, idx);
                    } else {
                        sb.append(full_Dictionary_fName);
                    }
                    int base_full_name_L = sb.length();
                    File f2 = new File(p, sb.append(".0.txt").toString());
                    if (f2.exists()) {
                        fZero = f2;
                        ftd = new ArrayList<>();
//                        handleDebugLines();
                    }
                    sb.setLength(base_full_name_L);
                    f2 = new File(p, sb.append(".mdd").toString());
                    if (f2.exists() && (mddCon == null || !mddCon.contains(f2.getPath()))) {
                        mdd = new ArrayList<>();
                        mdd.add(new MddFile(f2));
                        int cc = 1;
                        sb.setLength(base_full_name_L);
                        while ((f2 = new File(p, sb.append(".").append(cc++).append(".mdd").toString())).exists()) {
                            sb.setLength(base_full_name_L);
                            if (mddCon == null || !mddCon.contains(f2.getPath()))
                                mdd.add(new MddFile(f2));
                        }
                    }
                    //if(_header_tag.containsKey("SharedMdd")) {
                    //}
                }
            }
        }

        /* watch debug definition file (dictname.0.txt) */
//        public void handleDebugLines() {
//            if (ftd != null) {
//                long _fZero_LPT = fZero.lastModified();
//                if (_fZero_LPT != fZero_LPT) {
//                    ftd.clear();
//                    try {
//                        BufferedReader br = new BufferedReader(new FileReader(fZero));
//                        String line;
//                        while ((line = br.readLine()) != null) {
//                            handleDebugLines(line.trim());
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    fZero_LPT = _fZero_LPT;
//                }
//            }
//        }

        @Override
        protected void postGetCharset() {
            htmlOpenTag = "<".getBytes(_charset);
            htmlCloseTag = ">".getBytes(_charset);
            htmlTags = new byte[][]{htmlOpenTag, htmlCloseTag};
            htmlTagsA = new byte[][]{htmlOpenTag};
            htmlTagsB = new byte[][]{htmlCloseTag};
            switch (_charset.name()) {
                case "EUC-JP":
                    checkEven = 5;
                    maxEB = 3;
                    break;
                case "EUC-KR":
                case "x-EUC-TW":
                case "Shift_JIS":
                case "Windows-31j":
                    checkEven = 3;
                    maxEB = 2;
                    break;
                case "GB2312"://1981 unsafe double bytes
                case "GBK"://1995 unsafe double bytes
                    checkEven = 4;
                    maxEB = 2;
                    break;
                case "GB18030"://2000 unsafe double bytes
                    checkEven = 4;
                    maxEB = 4;
                    encodeChecker = new EncodeChecker();
                    break;
                case "UTF-16BE":
                case "UTF-16LE":
                    checkEven = 1;
                    maxEB = 4;
                    break;
                case "UTF-32BE":
                case "UTF-32LE":
                    checkEven = 2;
                    maxEB = 4;
                    break;
                case "Big5":// safe double bytes?
                case "Big5-HKSCS":// safe double bytes?
                    checkEven = 3;
                    break;
                case "UTF-8":// safe tripple bytes?
                    //checkEven=3;
                    maxEB = 4;
                    break;
                default:
                    maxEB = 1;
                    break;
            }
        }

        class EncodeChecker {
//            public boolean checkBefore(byte[] source, int sourceOffset, int fromIndex_, int ret) {
//                try {
//                    int code = source[sourceOffset + ret - 1] & 0xff;
//                    if (code <= 0x7F) {//1
//                        return true;
//                    }
//
//                    if (ret - 2 >= 0)
//                        if ((code >= 0x40 && code <= 0xFE && code != 0x7F)) {//2
//                            code = source[sourceOffset + ret - 2] & 0xff;//1
//                            if ((code >= 0x81 && code <= 0xFE)) {
//                                return true;
//                            }
//                        }
//
//                    if (ret - 4 >= 0)
//                        if ((code >= 0x30 && code <= 0x39)) {//4
//                            code = source[sourceOffset + ret - 2] & 0xff;
//                            if ((code >= 0x81 && code <= 0xFE)) {//3
//                                code = source[sourceOffset + ret - 3] & 0xff;
//                                if ((code >= 0x30 && code <= 0x39)) {//2
//                                    code = source[sourceOffset + ret - 4] & 0xff;
//                                    if ((code >= 0x81 && code <= 0xFE)) {//1
//                                        return true;
//                                    }
//                                }
//                            }
//                        }
//                } catch (Exception e) {
//                    StringUtils.Log(e);
//                }
//                return false;
//            }

//            public boolean checkAfter(byte[] source, int sourceOffset, int toIndex, int ret) {
//                try {
//                    int code = source[sourceOffset + ret] & 0xff;
//                    if (code <= 0x7F) {//1
//                        return true;
//                    }
//
//                    if (ret + 1 <= toIndex)
//                        if ((code >= 0x40 && code <= 0xFE && code != 0x7F)) {//2
//                            code = source[sourceOffset + ret + 1] & 0xff;//1
//                            if ((code >= 0x81 && code <= 0xFE)) {
//                                return true;
//                            }
//                        }
//
//                    if (ret + 3 <= toIndex)
//                        if ((code >= 0x81 && code <= 0xFE)) {//1
//                            code = source[sourceOffset + ret + 1] & 0xff;
//                            if ((code >= 0x30 && code <= 0x39)) {//2
//                                code = source[sourceOffset + ret + 2] & 0xff;
//                                if ((code >= 0x81 && code <= 0xFE)) {//3
//                                    code = source[sourceOffset + ret + 3] & 0xff;
//                                    if ((code >= 0x30 && code <= 0x39)) {//4
//                                        return true;
//                                    }
//                                }
//                            }
//                        }
//                } catch (Exception e) {
//                    StringUtils.Log(e);
//                }
//                return false;
//            }
        }
    }

    /**
     * Mdict java : resource file (.mdd) class
     *
     * @author KnIfER
     * @date 2017/12/8
     */
    public static class MddFile extends MdbFile {
        //构造
        public MddFile(File fn) throws IOException {
            super(fn, 0, null, null);
            //decode_record_block_header();
        }

        @Override
        protected void init(DataInputStream data_in) throws IOException {
            super.init(data_in);
            data_in.close();
        }
    }

    /**
     * @author KnIfER
     * @date 2018/05/31
     */
    private static class ByteUtils {//byteUtils

        static long toLong(byte[] buffer, int offset) {
            long values = 0;
            for (int i = 0; i < 8; i++) {
                values <<= 8;
                values |= (buffer[offset + i] & 0xff);
            }
            return values;
        }

        static int toInt(byte[] buffer, int offset) {
            int values = 0;
            for (int i = 0; i < 4; i++) {
                values <<= 8;
                values |= (buffer[offset + i] & 0xff);
            }
            return values;
        }


        static byte[] _fast_decrypt(byte[] data, byte[] key) {
            long previous = 0x36;
            for (int i = 0; i < data.length; i++) {
                //INCONGRUENT CONVERTION FROM byte to int
                int ddd = data[i] & 0xff;
                long t = (ddd >> 4 | ddd << 4) & 0xff;
                t = t ^ previous ^ (i & 0xff) ^ (key[(i % key.length)] & 0xff);
                previous = ddd;
                data[i] = (byte) t;
            }
            return data;
        }

        static byte[] _mdx_decrypt(byte[] comp_block) throws IOException {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write(comp_block, 4, 4);
            data.write(DictFileMdx.ripemd128.packIntLE(0x3695));
            byte[] key = DictFileMdx.ripemd128.ripemd128(data.toByteArray());
            data.reset();
            data.write(comp_block, 0, 8);
            byte[] comp_block2 = new byte[comp_block.length - 8];
            System.arraycopy(comp_block, 8, comp_block2, 0, comp_block.length - 8);
            data.write(_fast_decrypt(comp_block2, key));
            return data.toByteArray();
        }


        static char toChar(byte[] buffer, int offset) {
            char values = 0;
            for (int i = 0; i < 2; i++) {
                values <<= 8;
                values |= (buffer[offset + i] & 0xff);
            }
            return values;
        }


        static byte[] fileToByteArr(File f) {
            try {
                FileInputStream fin = new FileInputStream(f);
                byte[] data = new byte[(int) f.length()];
                fin.read(data);
                return data;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        static String fileToString(File f) {
            try {
                FileInputStream fin = new FileInputStream(f);
                byte[] data = new byte[(int) f.length()];
                fin.read(data);
                fin.close();
                return new String(data, "utf8");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }


    }

    private static class IntUtils {
        static int parsint(Object o) {
            return parsint(o, -1);
        }

        static int parsint(Object o, int val) {
        /*
    WARNING: This method may be invoked early during VM initialization
    before IntegerCache is initialized. Care must be taken to not use
    the valueOf method.
         */
            String s = String.valueOf(o);
            if (s == null) {
                return val;
            }

            int result = 0;
            boolean negative = false;
            int i = 0, len = s.length();
            int limit = -Integer.MAX_VALUE;
            int multmin;
            int digit;

            if (len > 0) {
                char firstChar = s.charAt(0);
                if (firstChar < '0') { // Possible leading "+" or "-"
                    if (firstChar == '-') {
                        negative = true;
                        limit = Integer.MIN_VALUE;
                    } else if (firstChar != '+')
                        return val;

                    if (len == 1) // Cannot have lone "+" or "-"
                        return val;
                    i++;
                }
                multmin = limit / 10;
                while (i < len) {
                    // Accumulating negatively avoids surprises near MAX_VALUE
                    digit = Character.digit(s.charAt(i++), 10);
                    if (digit < 0) {
                        return val;
                    }
                    if (result < multmin) {
                        return val;
                    }
                    result *= 10;
                    if (result < limit + digit) {
                        return val;
                    }
                    result -= digit;
                }
            } else {
                return val;
            }
            return negative ? result : -result;
        }


        static int reduce(String phrase, int start, int end) {//via mdict-js
            int len = end - start;
            if (len > 1) {
                len = len >> 1;

                //com.knziha.plod.CMN.show("asdasd");
                return phrase.compareTo(numOrder[start + len - 1]) > 0
                        ? reduce(phrase, start + len, end)
                        : reduce(phrase, start, start + len);
            } else {
                return phrase.compareTo(numOrder[start]) == 0 ? start : -1;
            }
        }

        static final Pattern hanziDelimeter = Pattern.compile("[十百千万]", Pattern.DOTALL);
        static final Pattern hanshuzi = Pattern.compile("[一七三两九二五八六四零十百千万]+", Pattern.DOTALL);
        static final Pattern shuzi = Pattern.compile("[0-9]+", Pattern.DOTALL);
        static final Pattern supportedHanShuZi = Pattern.compile("[十百千万]", Pattern.DOTALL);
        static final String[] numOrder = {"一", "七", "三", "两", "九", "二", "五", "八", "六", "四", "零"};
        static final int[] Numbers = {1, 7, 3, 2, 9, 2, 5, 8, 6, 4, 0};
        static final int[] Levels = {1, 10, 100, 1000, 10000};


        static int recurse1wCalc(String in, int start, int end, int CurrentLvMPlyer) {
            int _CurrentLvMultiplyer = CurrentLvMPlyer;
            int _CurrentLv = 0;
            int ret = 0;
            //com.knziha.plod.CMN.show("\r\ncalcStart:"+in.substring(start, end+1));
            while (end >= start) {
                String levelCharacter = in.substring(end, end + 1);
                //com.knziha.plod.CMN.show(start+"~~"+end+"~~"+ret);//levelCharacter
                int res = reduce(levelCharacter, 0, 11);
                if (res == -1) {//是数位符
                    int neoLv = 0;
                    if (levelCharacter.equals("十")) {
                        neoLv = 1;
                    } else if (levelCharacter.equals("百")) {
                        neoLv = 2;
                    } else if (levelCharacter.equals("千")) {
                        neoLv = 3;
                    } else {//if(levelCharacter.equals("万")) {
                        neoLv = 4;
                    }
                    if (end == start && neoLv == 1) {//十几
                        _CurrentLvMultiplyer = _CurrentLvMultiplyer * Levels[neoLv];
                        return 1 * _CurrentLvMultiplyer + ret;
                    } else if (neoLv > _CurrentLv) {//正常
                        _CurrentLvMultiplyer = CurrentLvMPlyer * Levels[neoLv];
                        //com.knziha.plod.CMN.show("正常"+_CurrentLvMultiplyer+"per"+CurrentLvMPlyer+"Levels[]"+Levels[neoLv]);
                        _CurrentLv = neoLv;
                    } else {//递归求前置修饰数
                        //com.knziha.plod.CMN.show("rererererererere");
                        return recurse1wCalc(in, start, end, 1) * _CurrentLvMultiplyer + ret;
                    }
                } else {//是数符
                    ret += Numbers[res] * _CurrentLvMultiplyer;
                }
                //com.knziha.plod.CMN.show(start+"--"+end+"~~"+ret);//levelCharacter
                end--;
            }
            return ret;
        }

    }

    private static class LinkastReUsageHashMap<K, V> extends LinkedHashMap<K, V> {
        int mCapacity;
        AtomicInteger accommodation;
        private Field f_accessOrder;
        static int BlockCacheSize = 1024;
        static final int BlockSize = 4096;
        private int desiredTotalCacheSize;
        private int perblockSize;

        LinkastReUsageHashMap(int initialCapacity, int desiredTotalCacheSize, int perblockSize) {
            super(initialCapacity, 1, desiredTotalCacheSize != 0);
            this.desiredTotalCacheSize = desiredTotalCacheSize;
            this.perblockSize = perblockSize;
            this.mCapacity = desiredTotalCacheSize / perblockSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            if (mCapacity > 0) {
                if (BlockCacheSize != desiredTotalCacheSize) {
                    desiredTotalCacheSize = BlockCacheSize;
                    this.mCapacity = desiredTotalCacheSize / perblockSize;
                }
                return size() > mCapacity;
            }
            return false;
        }
    }

    /**
     * Java ripemd128 from python
     *
     * @converter KnIfER
     * @date 2017/11/21
     */
    private static class ripemd128 {

        static long f(long j, long x, long y, long z) {
            assert (0 <= j && j < 64);
            if (j < 16)
                return x ^ y ^ z;
            else if (j < 32)
                return (x & y) | (z & ~x);
            else if (j < 48)
                return (x | (Long.valueOf("ffffffff", 16) & ~y)) ^ z;
            else
                return (x & z) | (y & ~z);
        }

        static long K(long j) {
            assert (0 <= j && j < 64);
            if (j < 16)
                return (long) 0x00000000;
            else if (j < 32)
                return (long) 0x5a827999;
            else if (j < 48)
                return (long) 0x6ed9eba1;
            else
                return (long) 0x8f1bbcdc;
        }

        static long Kp(long j) {
            assert (0 <= j && j < 64);
            if (j < 16)
                return (long) 0x50a28be6;
            else if (j < 32)
                return (long) 0x5c4dd124;
            else if (j < 48)
                return (long) 0x6d703ef3;
            else
                return (long) 0x00000000;

        }


        static long[][] padandsplit(byte[] message) throws IOException {
            /*
            returns a two-dimensional array X[i][j] of 32-bit integers, where j ranges
            from 0 to 16.
            First pads the message to length in bytes is congruent to 56 (mod 64), 
            by first adding a byte 0x80, and then padding with 0x00 bytes until the
            message length is congruent to 56 (mod 64). Then adds the little-endian
            64-bit representation of the original length. Finally, splits the result
            up into 64-byte blocks, which are further parsed as 32-bit integers.
            */
            //ByteBuffer sf = ByteBuffer.wrap(itemBuf);
            int origlen = message.length;
            //!!!INCONGRUENT CALCULATION METHOD
            int padlength = 64 - ((origlen - 56 + 64) % 64); //minimum padding is 1!
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write(message);
            data.write(new byte[]{(byte) 0x80});

            for (int i = 0; i < padlength - 1; i++) data.write(new byte[]{0x00});
            data.write(packLongLE(origlen * 8));
            assert (data.size() % 64 == 0);
            //System.out.println("origlen"+origlen);

            //System.out.print("message");printBytes(data.toByteArray());
            ByteBuffer sf = ByteBuffer.wrap(data.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            long[][] res = new long[data.size() / 64][64];
            for (int i = 0; i < data.size(); i += 64) {
                for (int j = 0; j < 64; j += 4)
                    res[i / 64][j / 4] = sf.getInt(i + j);
            }
            return res;
        }

        static byte[] packLongLE(long l) {
            return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong((long) l).array();
        }

        static byte[] packIntLE(int l) {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(l).array();
        }

        static long add(long... intArray) {
            long res = (long) 0;
            for (long i : intArray)
                res += i;
            return res & Long.valueOf("ffffffff", 16);
        }

        static long rol(long s, long x) {
            assert (s < 32);
            return (x << s | x >> (32 - s)) & Long.valueOf("ffffffff", 16);
            //before JAVA8 we do not have unsigned int
        }

        static int[] r = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
                3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
                1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2};
        static int[] rp = new int[]{5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
                6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
                15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
                8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14};
        static int[] s = new int[]{11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
                7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
                11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
                11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12};
        static int[] sp = new int[]{8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
                9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
                9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
                15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8};

        static byte[] ripemd128(byte[] message) throws IOException {
            long h0 = Long.valueOf("67452301", 16);
            long h1 = Long.valueOf("efcdab89", 16);
            long h2 = Long.valueOf("98badcfe", 16);
            long h3 = Long.valueOf("10325476", 16);
            long A, B, C, D, Ap, Bp, Cp, Dp;
            long[][] X = padandsplit(message);
            for (int i = 0; i < X.length; i++) {
                A = h0;
                B = h1;
                C = h2;
                D = h3;
                Ap = h0;
                Bp = h1;
                Cp = h2;
                Dp = h3;
                Long T;
                for (int j = 0; j < 64; j++) {
                    T = rol(s[j], add(A, f(j, B, C, D), X[i][r[j]], K(j)));
                    //System.out.println("preT is: "+add(A, f(j,B,C,D)));
                    //System.out.println("T is: "+T);
                    A = D;
                    D = C;
                    C = B;
                    B = T;
                    T = rol(sp[j], add(Ap, f(63 - j, Bp, Cp, Dp), X[i][rp[j]], Kp(j)));
                    //System.out.println("T2 is: "+T);
                    Ap = Dp;//System.out.println("Ap is: "+Ap);
                    Dp = Cp;//System.out.println("Dp is: "+Dp);
                    Cp = Bp;//System.out.println("Cp is: "+Cp);
                    Bp = T;//System.out.println("Bp is: "+Bp);
                }
                T = add(h1, C, Dp);
                //System.out.println("T3 is: "+T);
                h1 = add(h2, D, Ap);
                h2 = add(h3, A, Bp);
                h3 = add(h0, B, Cp);
                h0 = T;
            }

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            //struct.pack("<LLLL",h0,h1,h2,h3)
            data.write(packIntLE((int) h0));
            data.write(packIntLE((int) h1));
            data.write(packIntLE((int) h2));
            data.write(packIntLE((int) h3));
            return data.toByteArray();
        }


    }

    private static class myCpr<T1 extends Comparable<T1>, T2> implements Comparable<myCpr<T1, T2>> {
        T1 key;
        T2 value;

        myCpr(T1 k, T2 v) {
            key = k;
            value = v;
        }

        public int compareTo(myCpr<T1, T2> other) {
            if (key.getClass() == String.class) {
                return (MdxFile.processText((String) key).compareTo(MdxFile.processText((String) other.key)));
            } else
                return this.key.compareTo(other.key);
        }

        public String toString() {
            return key + "_" + value;
        }
    }

    private static class RBTNode<T extends Comparable<T>> {
        private static final boolean RED = false;
        private static final boolean BLACK = true;
        boolean color;        // 颜色
        T key;
        RBTNode<T> left;

        RBTNode<T> right;

        RBTNode<T> parent;

        RBTNode(T key, boolean color, RBTNode<T> parent, RBTNode<T> left, RBTNode<T> right) {
            this.key = key;
            this.color = color;
            this.parent = parent;
            this.left = left;
            this.right = right;
        }

        T getKey() {
            return key;
        }

        public String toString() {
            return "" + key + (this.color == RED ? "(R)" : "B");
        }
    }

    /**
     * Java 语言: 红黑树
     *
     * @author skywang
     * @date 2013/11/07
     * @editor KnIfER
     * @date 2017/11/18
     */
    private static class RBTree<T extends Comparable<T>> {

        protected RBTNode<T> mRoot;

        protected static final boolean RED = false;
        protected static final boolean BLACK = true;
        int size = 0;


        RBTree() {
            mRoot = null;
        }

        private RBTNode<T> parentOf(RBTNode<T> node) {
            return node != null ? node.parent : null;
        }

        private boolean isRed(RBTNode<T> node) {
            return ((node != null) && (node.color == RED)) ? true : false;
        }

        private void setBlack(RBTNode<T> node) {
            if (node != null)
                node.color = BLACK;
        }

        private void setRed(RBTNode<T> node) {
            if (node != null)
                node.color = RED;
        }

        //![4]
        //![5]此处放大招！!
        //下行wrap :find node x,so that x.key=<val and no node with key greater that x.key satisfies this condition.
        RBTNode<T> xxing(T val) {
            RBTNode<T> tmpnode = downwardNeighbour(this.mRoot, val);
            if (tmpnode != null) return tmpnode;
            else return this.minimum(this.mRoot);
        }

        ///情况二///cur///情况一/
        private RBTNode<T> downwardNeighbour(RBTNode<T> du, T val) {
            int cmp;
            RBTNode<T> x = du;
            RBTNode<T> tmpnode = null;

            if (x == null)
                return null;

            cmp = val.compareTo(x.key);
            if (cmp < 0)//情况一
                return downwardNeighbour(x.left, val);
            else// if (cmp >= 0)//情况二
            {
                if (x.right == null) return x;
                tmpnode = downwardNeighbour(x.right, val);
                if (tmpnode == null) return x;
                else return tmpnode;
            }
        }


        /*
         * 查找最小结点：返回tree为根结点的红黑树的最小结点。
         */
        private RBTNode<T> minimum(RBTNode<T> tree) {
            if (tree == null)
                return null;

            while (tree.left != null)
                tree = tree.left;
            return tree;
        }

        /*
         * 对红黑树的节点(x)进行左旋转
         *
         * 左旋示意图(对节点x进行左旋)：
         *      px                              px
         *     /                               /
         *    x                               y
         *   /  \      --(左旋)-.           / \                #
         *  lx   y                          x  ry
         *     /   \                       /  \
         *    ly   ry                     lx  ly
         *
         *
         */
        private void leftRotate(RBTNode<T> x) {
            // 设置x的右孩子为y
            RBTNode<T> y = x.right;

            // 将 “y的左孩子” 设为 “x的右孩子”；
            // 如果y的左孩子非空，将 “x” 设为 “y的左孩子的父亲”
            x.right = y.left;
            if (y.left != null)
                y.left.parent = x;

            // 将 “x的父亲” 设为 “y的父亲”
            y.parent = x.parent;

            if (x.parent == null) {
                this.mRoot = y;            // 如果 “x的父亲” 是空节点，则将y设为根节点
            } else {
                if (x.parent.left == x)
                    x.parent.left = y;    // 如果 x是它父节点的左孩子，则将y设为“x的父节点的左孩子”
                else
                    x.parent.right = y;    // 如果 x是它父节点的左孩子，则将y设为“x的父节点的左孩子”
            }

            // 将 “x” 设为 “y的左孩子”
            y.left = x;
            // 将 “x的父节点” 设为 “y”
            x.parent = y;
        }

        /*
         * 对红黑树的节点(y)进行右旋转
         *
         * 右旋示意图(对节点y进行左旋)：
         *            py                               py
         *           /                                /
         *          y                                x
         *         /  \      --(右旋)-.            /  \                     #
         *        x   ry                           lx   y
         *       / \                                   / \                   #
         *      lx  rx                                rx  ry
         *
         */

        private void rightRotate(RBTNode<T> y) {
            // 设置x是当前节点的左孩子。
            RBTNode<T> x = y.left;

            // 将 “x的右孩子” 设为 “y的左孩子”；
            // 如果"x的右孩子"不为空的话，将 “y” 设为 “x的右孩子的父亲”
            y.left = x.right;
            if (x.right != null)
                x.right.parent = y;

            // 将 “y的父亲” 设为 “x的父亲”
            x.parent = y.parent;

            if (y.parent == null) {
                this.mRoot = x;            // 如果 “y的父亲” 是空节点，则将x设为根节点
            } else {
                if (y == y.parent.right)
                    y.parent.right = x;    // 如果 y是它父节点的右孩子，则将x设为“y的父节点的右孩子”
                else
                    y.parent.left = x;    // (y是它父节点的左孩子) 将x设为“x的父节点的左孩子”
            }

            // 将 “y” 设为 “x的右孩子”
            x.right = y;

            // 将 “y的父节点” 设为 “x”
            y.parent = x;
        }

        /*
         * 红黑树插入修正函数
         *
         * 在向红黑树中插入节点之后(失去平衡)，再调用该函数；
         * 目的是将它重新塑造成一颗红黑树。
         *
         * 参数说明：
         *     node 插入的结点        // 对应《算法导论》中的z
         */
        protected void insertFixUp(RBTNode<T> node) {
            RBTNode<T> parent, gparent;

            // 若“父节点存在，并且父节点的颜色是红色”
            while (((parent = parentOf(node)) != null) && isRed(parent)) {
                gparent = parentOf(parent);

                //若“父节点”是“祖父节点的左孩子”
                if (parent == gparent.left) {
                    // Case 1条件：叔叔节点是红色
                    RBTNode<T> uncle = gparent.right;
                    if ((uncle != null) && isRed(uncle)) {
                        setBlack(uncle);
                        setBlack(parent);
                        setRed(gparent);
                        node = gparent;
                        continue;
                    }

                    // Case 2条件：叔叔是黑色，且当前节点是右孩子
                    if (parent.right == node) {
                        RBTNode<T> tmp;
                        leftRotate(parent);
                        tmp = parent;
                        parent = node;
                        node = tmp;
                    }

                    // Case 3条件：叔叔是黑色，且当前节点是左孩子。
                    setBlack(parent);
                    setRed(gparent);
                    rightRotate(gparent);
                } else {    //若“z的父节点”是“z的祖父节点的右孩子”
                    // Case 1条件：叔叔节点是红色
                    RBTNode<T> uncle = gparent.left;
                    if ((uncle != null) && isRed(uncle)) {
                        setBlack(uncle);
                        setBlack(parent);
                        setRed(gparent);
                        node = gparent;
                        continue;
                    }

                    // Case 2条件：叔叔是黑色，且当前节点是左孩子
                    if (parent.left == node) {
                        RBTNode<T> tmp;
                        rightRotate(parent);
                        tmp = parent;
                        parent = node;
                        node = tmp;
                    }

                    // Case 3条件：叔叔是黑色，且当前节点是右孩子。
                    setBlack(parent);
                    setRed(gparent);
                    leftRotate(gparent);
                }
            }

            // 将根节点设为黑色
            setBlack(this.mRoot);
        }

        /*
         * 将结点插入到红黑树中
         *
         * 参数说明：
         *     node 插入的结点        // 对应《算法导论》中的node
         */
        private void insert(RBTNode<T> node) {
            int cmp;
            RBTNode<T> y = null;
            RBTNode<T> x = this.mRoot;

            // 1. 将红黑树当作一颗二叉查找树，将节点添加到二叉查找树中。
            while (x != null) {
                y = x;
                cmp = node.key.compareTo(x.key);
                if (cmp < 0)
                    x = x.left;
                else if (cmp > 0)
                    x = x.right;
                else return;
            }

            node.parent = y;
            if (y != null) {
                cmp = node.key.compareTo(y.key);
                if (cmp < 0)
                    y.left = node;
                else
                    y.right = node;
            } else {
                this.mRoot = node;
            }

            // 2. 设置节点的颜色为红色
            node.color = RED;

            size++;

            // 3. 将它重新修正为一颗二叉查找树
            insertFixUp(node);
        }

        /*
         * 新建结点(key)，并将其插入到红黑树中
         *
         * 参数说明：
         *     key 插入结点的键值
         */
        void insert(T key) {
            insert(new RBTNode<>(key, BLACK, null, null, null));
        }
    }
}
