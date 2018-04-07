import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Created by Alex on 06.04.2018.
 */
public class main
{
    public static void main( String[] args )
    {
        try
        {
            Process p = Runtime.getRuntime().exec( "python -u \"D:\\git\\Licenta\\Discuri\\WIP\\python ICP.py\"" );

            OutputStream out = p.getOutputStream();
            InputStream inp = p.getInputStream();
            InputStream err = p.getErrorStream();

            ByteBuffer byteBuffer = ByteBuffer.allocate( 1024 );
            byteBuffer.putDouble( 1 );
            byteBuffer.putDouble( 10 );
            byteBuffer.putDouble( 100 );
            byteBuffer.putDouble( 1000 );

            out.write( byteBuffer.array(), 0, 8 * 4 );
            out.flush();

            byteBuffer.clear();
            byteBuffer.rewind();

            int read = inp.read( byteBuffer.array());
            System.out.println( read );

            byteBuffer.rewind();
            System.out.println( byteBuffer.getDouble() );
            System.out.println( byteBuffer.getDouble() );
            System.out.println( byteBuffer.getDouble() );
            System.out.println( byteBuffer.getDouble() );
            //System.out.println( StandardCharsets.ISO_8859_1.decode( byteBuffer ).toString().substring( 0, read ) );

            inp.close();
            out.close();

            p.destroy();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }
}
