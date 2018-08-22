import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioFileFormat.Type;

class WaveData {
    // Data variables
    private AudioFormat format;
    private long sampleLength;
    private int []sampleArray;

    // Constructor
    public WaveData() {
        format = null;
        sampleLength = 0;
        sampleArray = null;
    }
    
    // get format
    public AudioFormat getFormat()
    {
        return format;
    }

    // get length of sample array
    public long getSampleLength()
    {
        return sampleLength;
    }

    // get sample array
    public int[] getArray()
    {
        return sampleArray;
    }

    // for DEBUG
    public void displayRecodableFormat()
    {
        try {
            Mixer mixer = AudioSystem.getMixer(null); // default mixer
            mixer.open();
            System.out.printf("Default mixer (%s)\n", mixer.getMixerInfo().getName());
            for(Line.Info info : mixer.getSourceLineInfo()) {
                if(SourceDataLine.class.isAssignableFrom(info.getLineClass())) {
                    SourceDataLine.Info info2 = (SourceDataLine.Info) info;
                    System.out.println(info2);
                    System.out.printf("  max buffer size: \t%d\n", info2.getMaxBufferSize());
                    System.out.printf("  min buffer size: \t%d\n", info2.getMinBufferSize());
                    AudioFormat[] formats = info2.getFormats();
                    System.out.println("  Supported Audio formats: ");
                    for(AudioFormat format : formats) {
                        System.out.println("    "+format);
                    }
                    System.out.println();
                } else {
                    System.out.println(info.toString());
                }
                System.out.println();
            }
            mixer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Record from microphone/line input
    public void record(float sampleRate, int sampleSizeInBits, long sampleLength) {
        try {
            makeFormat(sampleRate, sampleSizeInBits);
            allocateSampleArray(sampleLength);
    
            TargetDataLine line = AudioSystem.getTargetDataLine(format);
            line.open(format);
            AudioInputStream ais = new AudioInputStream(line);
            BufferedInputStream bis = new BufferedInputStream(ais);
            byte []frameByteBuffer = new byte[format.getFrameSize()]; /* 1フレーム分 */
            int channel = 0;
            boolean isBigEndian = format.isBigEndian();
            int sampleSizeInBytes = format.getSampleSizeInBits() / 8;
            line.start();
            for (int i = 0; i < sampleLength;) {
                /* 1フレーム分取り出し */
                if(bis.read(frameByteBuffer) > 0)
                {
                    int v = 0;
                    // 1フレームから1サンプル分取り出し
                    if(sampleSizeInBytes == 1) {
                        // 8bitの場合
                        //
                        // 8bitは特別な処理
                        // wavファイルは符号無しで値の範囲は0から255（サンプル値としては-128から127）だが，
                        // Javaの符号付byteに読み込まれ-128から128で表現される．
                        // このため，Javaのbyteからサンプル値への変換は，
                        // 0から127を-128から-1に，-128から-1を0から127に変換する
                        v = frameByteBuffer[0]>=0?(int)(frameByteBuffer[0]-128):(128+frameByteBuffer[0]);
                    } else {
                        // 8bit以外の場合
                        if(isBigEndian) {
                            // Big endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channel * sampleSizeInBytes + j] & 0xff) << (8 * (sampleSizeInBytes - j - 1)));
                            }
                        } else {
                            // Little endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channel * sampleSizeInBytes + j] & 0xff) << (8 * j));
                            }
                        }
                    }
                    sampleArray[i++] = v;
                }
            }
            line.stop();
            line.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Make empty data
    public void create(float sampleRate, int sampleSizeInBits, long sampleLength) {
        makeFormat(sampleRate, sampleSizeInBits);
        allocateSampleArray(sampleLength);
    }

    private void makeFormat(float sampleRate, int sampleSizeInBits) {
        int channels = 1;
        boolean signed = (sampleSizeInBits == 8)?false:true;
        boolean bigEndian = false;
        format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    private void allocateSampleArray(long sampleLength) {
        this.sampleLength = sampleLength;
        sampleArray = new int[(int)sampleLength];
    }

    // Write wav file
    public void writeFile(String filePath) {
        try {
            boolean isBigEndian = format.isBigEndian();
            int sampleSizeInBytes = format.getSampleSizeInBits() / 8; 
            byte [] byteArray = new byte[(int)(sampleLength * sampleSizeInBytes)];
            for(int i = 0; i < sampleLength; i++) {
                if(sampleSizeInBytes == 1) {
                    byteArray[i] = sampleArray[i]<0?(byte)(sampleArray[i]+128):(byte)(sampleArray[i]-128);
                } else {
                    if(isBigEndian) {
                        // Big endian
                        for(int j = 0; j < sampleSizeInBytes; j++) {
                            byteArray[i*sampleSizeInBytes + j] = (byte)((sampleArray[i] >> ((sampleSizeInBytes-j-1)*8)) & 0xff);
                        }
                    } else {
                        // Little endian
                        for(int j = 0; j < sampleSizeInBytes; j++) {
                            byteArray[i*sampleSizeInBytes + j] = (byte)((sampleArray[i] >> (j*8)) & 0xff);
                        }
                    }
                }
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            AudioInputStream ais = new AudioInputStream(bis, format, sampleLength);
            File file = new File(filePath);
            AudioSystem.write(ais, Type.WAVE, file);
            ais.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read wav file
    public void readFile(String filePath) {
        try {
            File file = new File(filePath);
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            format = in.getFormat();
            sampleLength = in.getFrameLength();
            sampleArray = new int[(int)sampleLength];
            BufferedInputStream bis = new BufferedInputStream(in);
            boolean isBigEndian = format.isBigEndian();
            int sampleSizeInBytes = format.getSampleSizeInBits() / 8;
            int channel = 0;
            byte []frameByteBuffer = new byte[format.getFrameSize()]; /* 1フレーム分 */
            int zeroOffset = 1 * (0x80 << 8*(sampleSizeInBytes-1));
            /*
            # Frame
            FrameSize = (SampleSizeInBits / 8) * number_of_Channles.
            ## 2ch.
            ->|-----|<- Frame
              |L0|R0|L1|R1|...
            ->|--|<- Sample

            ## 1ch.
            ->|--|<- Frame
              |L0|L1|...
            ->|--|<- Sample
            
            # Sample
            ## SampleSizeInBits = 8, (sampleSizeInBytes = 1)
            ->|-------|<- Sample
              |b0...b7|b0...b7|
            ## SampleSizeInBits = 16, (sampleSizeInBytes = 2)
            ->|---------------|<- Sample
              |b0...b7b8...b15|b0...
            */
            for (int i = 0; i < sampleLength; i++) {
                /* 1フレーム分ファイルから読み込み */
                if(bis.read(frameByteBuffer) > 0)
                {
                    int v = 0;
                    // 1フレームから1サンプル分取り出し
                    if(sampleSizeInBytes == 1) {
                        // 8bitの場合
                        //
                        // 8bitは特別な処理
                        // wavファイルは符号無しで値の範囲は0から255（サンプル値としては-128から127）だが，
                        // Javaの符号付byteに読み込まれ-128から128で表現される．
                        // このため，Javaのbyteからサンプル値への変換は，
                        // 0から127を-128から-1に，-128から-1を0から127に変換する
                        v = frameByteBuffer[0]>=0?(int)(frameByteBuffer[0]-128):(128+frameByteBuffer[0]);
                    } else {
                        // 8bit以外の場合
                        if(isBigEndian) {
                            // Big endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channel * sampleSizeInBytes + j] & 0xff) << (8 * (sampleSizeInBytes - j - 1)));
                            }
                        } else {
                            // Little endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channel * sampleSizeInBytes + j] & 0xff) << (8 * j));
                            }
                        }
                        // 符号拡張
                        if ((v & (0x80 << 8*(sampleSizeInBytes-1))) != 0) {
                            v += (0xffffffff << (8*sampleSizeInBytes));
                        }
                    }
                    sampleArray[i] = v;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class WaveReadWriteTest {

    public static void sample1() {
        /* sample-1 (16bit)ファイルを読み込んで，そのまま書き込むテスト */
        // WaveDataクラスのオブジェクトを生成
        WaveData wf = new WaveData();
        // 読み込み
        wf.readFile("./f1k_44100_16mono.wav");
        // 書き込み
        wf.writeFile("./f1k_44100_16out.wav");
        // 最初の10サンプルの値を画面へ出力
        int []data = wf.getArray();    // 格納用配列を取得
        System.out.println("Start sample1");
        for(int i=0; i<10; i++) {
            System.out.println(data[i]);
        }
        AudioFormat f = wf.getFormat();
        System.out.println(f.toString());
        /* sample-1 ここまで */
    }

    public static void sample1b() {
        /* sample-1b (8bit)ファイルを読み込んで，そのまま書き込むテスト */
        // WaveDataクラスのオブジェクトを生成
        WaveData wf = new WaveData();
        // 読み込み
        wf.readFile("./f1k_44100_8mono.wav");
        // 書き込み
        wf.writeFile("./f1k_44100_8out.wav");
        // 最初の10サンプルの値を画面へ出力
        int []data = wf.getArray();    // 格納用配列を取得
        System.out.println("Start sample1b");
        for(int i=0; i<10; i++) {
            System.out.println(data[i]);
        }
        AudioFormat f = wf.getFormat();
        System.out.println(f.toString());
        /* sample-1b ここまで */
    }

    public static void sample2() {
        /* sample-2 空のデータに400Hzのsin波形を生成して，ファイルに書き込む */
        // WaveDataクラスのオブジェクトを生成
        WaveData wf2 = new WaveData();
        int fs = 44100; // fs=44.1kHz
        wf2.create((float)fs, 16, fs*1); // , 16bitで1秒間(44100サンプル)のデータ領域を用意
        int []array = wf2.getArray();    // 格納用配列を取得
        // sin波形を生成して配列へ
        for(int i=0; i<array.length; i++) {
            // 振幅30000，周波数400Hzのsin波
            array[i] = (int)(30000.0*Math.sin(2.0*Math.PI*400.0*i/44100.0));
        }
        // 書き込み
        wf2.writeFile("./f400_44100_16out.wav");
        /* sample-2 ここまで */
    }

    public static void sample3() {
        /* sample-3 空のデータに400Hzのsin波形を生成して，ファイルに書き込む */
        // WaveDataクラスのオブジェクトを生成
        WaveData wf3 = new WaveData();
        int fs = 44100; // fs=44.1kHz
        wf3.record((float)fs, 16, fs*3); // fs=44.1kHz, 16bitで3秒間，録音する
        // 書き込み
        wf3.writeFile("./mic_44100_16out.wav");
        /* sample-3 ここまで */
    }

    public static void main(String[] args) {
        
        sample1();
        //sample1b();
        sample2();
        
        //WaveData wf4 = new WaveData(); wf4.displayRecodableFormat(); // DEBUG
        //sample3();
    }
}