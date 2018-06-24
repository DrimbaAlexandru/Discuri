import MarkerFile.*;
import Utils.Interval;

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
            mf.add_from_file( "D:\\training sets\\resampled\\Enescu - Rapsodia romana nr. 2 in re major op. 11 nr. 2 mark 0,0100.txt" );
            mf.add_from_file( "D:\\training sets\\resampled\\Enescu - Rapsodia romana nr. 2 in re major op. 11 nr. 2 mark pop s 5 m 5 0,1000.txt" );
            mf.writeMarkingsToFile(new FileWriter( "D:\\training sets\\resampled\\Enescu - Rapsodia romana nr. 2 in re major op. 11 nr. 2 mark 0,01 + pop 0,10.txt" ));
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
