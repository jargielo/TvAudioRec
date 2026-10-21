package pl.n2s.grabberpie;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public final class WaveHelper {
 public static byte[] header(long data,int rate,int channels){
  ByteBuffer b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
  b.put(new byte[]{'R','I','F','F'});b.putInt((int)(36+data));
  b.put(new byte[]{'W','A','V','E','f','m','t',' '});b.putInt(16);
  b.putShort((short)1);b.putShort((short)channels);b.putInt(rate);
  b.putInt(rate*channels*2);b.putShort((short)(channels*2));b.putShort((short)16);
  b.put(new byte[]{'d','a','t','a'});b.putInt((int)data);
  return b.array();
 }
 private WaveHelper(){}
}
