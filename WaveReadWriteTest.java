import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioFileFormat.Type;

class WaveData {
    // Data variables
    private AudioFormat format;
    private long sampleLength;
    private double []sampleArray;

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
    public double[] getArray()
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

    private byte [] covertSampleToByteArray()
    {
        boolean isBigEndian = format.isBigEndian();
        int sampleSizeInBytes = format.getSampleSizeInBits() / 8; 
        byte [] byteArray = new byte[(int)(sampleLength * sampleSizeInBytes)];
        int normalizingFactor = (0x80 << (8*(sampleSizeInBytes - 1)));

        for(int i = 0; i < sampleLength; i++) {
            double sample = sampleArray[i];
            // �T���v���l�͈̔͂�[-1, 1)
            if(!(sample >= -1.0 && sample < 1.0)){
                System.err.printf("Warning! Sample[%d] is out of range.\n", i);
                // Clipping
                sample = (sample<0)?-1.0:0.99;
            }

            int sampleInt = (int)(sample*normalizingFactor);
            if(sampleSizeInBytes == 1) {
                byteArray[i] = sampleInt<0?(byte)(sampleInt+128):(byte)(sampleInt-128);
            } else {
                if(isBigEndian) {
                    // Big endian
                    for(int j = 0; j < sampleSizeInBytes; j++) {
                        byteArray[i*sampleSizeInBytes + j] = (byte)((sampleInt >> ((sampleSizeInBytes-j-1)*8)) & 0xff);
                    }
                } else {
                    // Little endian
                    for(int j = 0; j < sampleSizeInBytes; j++) {
                        byteArray[i*sampleSizeInBytes + j] = (byte)((sampleInt >> (j*8)) & 0xff);
                    }
                }
            }
        }
        return byteArray;
    }

    private void covertBufferedInputStreamToSample(BufferedInputStream bis, int channelNo)
    {
        allocateSampleArray(sampleLength);
        boolean isBigEndian = format.isBigEndian();
        int sampleSizeInBytes = format.getSampleSizeInBits() / 8;
        byte []frameByteBuffer = new byte[format.getFrameSize()]; /* 1�t���[���� */
        int normalizingFactor = (0x80 << (8*(sampleSizeInBytes - 1)));
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
        try {
            int v;
            for (int i = 0; i < sampleLength; i++) {
                /* 1�t���[�����t�@�C������ǂݍ��� */
                if(bis.read(frameByteBuffer) > 0)
                {
                    v = 0;
                    // 1�t���[������1�T���v�������o��
                    if(sampleSizeInBytes == 1) {
                        // 8bit�̏ꍇ
                        //
                        // 8bit�͓��ʂȏ���
                        // wav�t�@�C���͕��������Œl�͈̔͂�0����255�i�T���v���l�Ƃ��Ă�-128����127�j�����C
                        // Java�̕����tbyte�ɓǂݍ��܂�-128����128�ŕ\�������D
                        // ���̂��߁CJava��byte����T���v���l�ւ̕ϊ��́C
                        // 0����127��-128����-1�ɁC-128����-1��0����127�ɕϊ�����
                        v = frameByteBuffer[0]>=0?(int)(frameByteBuffer[0]-128):(128+frameByteBuffer[0]);
                    } else {
                        // 8bit�ȊO�̏ꍇ
                        if(isBigEndian) {
                            // Big endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channelNo * sampleSizeInBytes + j] & 0xff) << (8 * (sampleSizeInBytes - j - 1)));
                            }
                        } else {
                            // Little endian
                            for (int j = 0; j < sampleSizeInBytes; j++) {
                                v += (((int)frameByteBuffer[channelNo * sampleSizeInBytes + j] & 0xff) << (8 * j));
                            }
                        }
                        // �����g��
                        if ((v & (0x80 << 8*(sampleSizeInBytes-1))) != 0) {
                            v += (0xffffffff << (8*sampleSizeInBytes));
                        }
                    }
                    sampleArray[i] = ((double)v / (double)normalizingFactor);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Play sound
    public void play() {
        try {
            // prepare output line
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine s = (SourceDataLine)AudioSystem.getLine(info);
            // get sample data            
            byte [] byteArray = covertSampleToByteArray();

            s.open();
            s.start();
            
            s.write(byteArray, 0, byteArray.length);
            
            s.drain();
            s.stop();
            s.close();
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
            int channelNo = 0;
            line.start();

            covertBufferedInputStreamToSample(bis, channelNo);

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
        sampleArray = new double[(int)sampleLength];
    }

    // Write wav file
    public void writeFile(String filePath) {
        try {
            byte [] byteArray = covertSampleToByteArray();
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
            format = in.getFormat(); // set member var
            sampleLength = in.getFrameLength(); // set member var
            BufferedInputStream bis = new BufferedInputStream(in);
            int channelNo = 0;
            covertBufferedInputStreamToSample(bis, channelNo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class WaveReadWriteTest {

    public static void sample1() {
        /* sample-1 (16bit)�t�@�C����ǂݍ���ŁC���̂܂܏������ރe�X�g */
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf = new WaveData();
        // �ǂݍ���
        wf.readFile("./f1k_44100_16mono.wav");
        // ��������
        wf.writeFile("./f1k_44100_16out.wav");
        wf.play();

        // �ŏ���10�T���v���̒l����ʂ֏o��
        double []data = wf.getArray();    // �i�[�p�z����擾
        System.out.println("Start sample1");
        for(int i=0; i<50; i++) {
            System.out.println(data[i]);
        }
        AudioFormat f = wf.getFormat();
        System.out.println(f.toString());
        /* sample-1 �����܂� */
    }

    public static void sample1b() {
        /* sample-1b (8bit)�t�@�C����ǂݍ���ŁC���̂܂܏������ރe�X�g */
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf = new WaveData();
        // �ǂݍ���
        wf.readFile("./f1k_44100_8mono.wav");
        // ��������
        wf.writeFile("./f1k_44100_8out.wav");
        // �ŏ���10�T���v���̒l����ʂ֏o��
        double []data = wf.getArray();    // �i�[�p�z����擾
        System.out.println("Start sample1b");
        for(int i=0; i<50; i++) {
            System.out.println(data[i]);
        }
        AudioFormat f = wf.getFormat();
        System.out.println(f.toString());
        /* sample-1b �����܂� */
    }

    public static void sample2() {
        /* sample-2 ��̃f�[�^��400Hz��sin�g�`�𐶐����āC�t�@�C���ɏ������� */
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf2 = new WaveData();
        int fs = 44100; // fs=44.1kHz
        wf2.create((float)fs, 16, fs*1); // , 16bit��1�b��(44100�T���v��)�̃f�[�^�̈��p��
        double []array = wf2.getArray();    // �i�[�p�z����擾
        // sin�g�`�𐶐����Ĕz���
        for(int i=0; i<array.length; i++) {
            // �U��0.9�C���g��400Hz��sin�g
            array[i] = (0.9*Math.sin(2.0*Math.PI*400.0*i/44100.0));
        }
        // ��������
        wf2.writeFile("./f400_44100_16out.wav");
        /* sample-2 �����܂� */
    }

    public static void sample3() {
        /* sample-3 ��̃f�[�^��400Hz��sin�g�`�𐶐����āC�t�@�C���ɏ������� */
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf3 = new WaveData();
        int fs = 44100; // fs=44.1kHz
        wf3.record((float)fs, 16, fs*3); // fs=44.1kHz, 16bit��3�b�ԁC�^������
        // ��������
        wf3.writeFile("./mic_44100_16out.wav");
        /* sample-3 �����܂� */
    }

    public static void sample4() {
        /* sample-4 (16bit)�t�@�C����ǂݍ���ŁC���̂܂܍Đ��e�X�g */
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf = new WaveData();
        // �ǂݍ���
        wf.readFile("./bensound-retrosoul_intro.wav");
        // �Đ�
        wf.play();
        /* sample-4 �����܂� */
    }

    public static void checkRangeAssert() {
        // WaveData�N���X�̃I�u�W�F�N�g�𐶐�
        WaveData wf = new WaveData();
        int fs = 44100; // fs=44.1kHz
        wf.create((float)fs, 16, fs*1); // , 16bit��1�b��(44100�T���v��)�̃f�[�^�̈��p��
        double []array = wf.getArray();    // �i�[�p�z����擾
        // sin�g�`�𐶐����Ĕz���
        for(int i=0; i<array.length; i++) {
            // �U��1.1�C���g��400Hz��sin�g
            array[i] = (1.1*Math.sin(2.0*Math.PI*400.0*i/44100.0));
        }
        // ��������(�T���v���l�͈̔͂�[-1, +1)�𒴂��邽��Warning���o��͂�)
        wf.writeFile("./checkAssert_f400_44100_16out.wav");
    }

    public static void main(String[] args) {
        
        //sample1();
        //sample1b();
        //sample2();
        
        //WaveData wf4 = new WaveData(); wf4.displayRecodableFormat(); // DEBUG
        //sample3();
        //sample4();
        
        checkRangeAssert();
    }
}