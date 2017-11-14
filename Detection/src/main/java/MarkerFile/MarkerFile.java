package MarkerFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 10.11.2017.
 */
public class MarkerFile
{
    List< Marking > markings = new ArrayList<>();

    public MarkerFile()
    {
    }

    public void addMark( int fsi, int len, int ch )
    {
        markings.add( new Marking( fsi, len, ch ) );
    }

    public void writeMarkingsToFile( OutputStreamWriter o )
    {
        for( Marking m : markings )
        {
            try
            {
                o.write( m.first_sample_index + " " + m.length + " " + m.channel + "r\n" );
            }
            catch( IOException e )
            {
                e.printStackTrace();
            }
        }
    }

}
