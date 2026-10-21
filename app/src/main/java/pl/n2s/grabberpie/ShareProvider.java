package pl.n2s.grabberpie;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
public final class ShareProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 private File get(Uri uri)throws FileNotFoundException{
   String name=uri.getLastPathSegment();
   if(name==null||name.isEmpty()||name.contains("/")||name.contains("\\")||name.contains(".."))
     throw new FileNotFoundException("Invalid name");
   File base=getContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC);
   if(base==null)base=new File(getContext().getFilesDir(),"Music");
   File f=new File(base,name);if(!f.isFile())throw new FileNotFoundException(name);return f;
 }
 @Override public ParcelFileDescriptor openFile(Uri u,String m)throws FileNotFoundException{
   if(!"r".equals(m))throw new FileNotFoundException("Read only");
   return ParcelFileDescriptor.open(get(u),ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public String getType(Uri u){return u.getLastPathSegment()!=null&&u.getLastPathSegment().endsWith(".wav")?"audio/wav":"audio/mp4";}
 @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String order){
   try{
     File f=get(u);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
     c.addRow(new Object[]{f.getName(),f.length()});return c;
   }catch(Exception ex){return null;}
 }
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){return 0;}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
