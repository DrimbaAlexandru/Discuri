import Utils.DataStructures.MarkerFile.*;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;

/**
 * Created by Alex on 16.06.2018.
 */
public class MarkerFileSummer
{
    public static void main( String args[] )
    {
        try
        {
            MarkerFile mf = new MarkerFile();
            mf.add_from_file( "D:\\training sets\\resampled\\dvorak 4th symph fin mark 96000.txt" );
            mf.add_from_file( "D:\\training sets\\resampled\\dvorak 4th symph fin mark pop s 3 m 3 0,0250.txt" );
            mf.writeMarkingsToFile(new FileWriter( "D:\\training sets\\resampled\\dvorak 4th symph fin mark + 0,025.txt" ));
        }
        catch( FileNotFoundException e )
        {
            e.printStackTrace();
        }
        catch( ParseException e )
        {
            e.printStackTrace();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }
}
