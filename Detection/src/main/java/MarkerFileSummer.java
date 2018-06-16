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
            MarkerFile mf1 = MarkerFile.fromFile( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth mark h 0,003.txt" );
            MarkerFile mf2 = MarkerFile.fromFile( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth mark l 0,005.txt" );
            for( Marking m : mf2.get_all_markings( new Interval( 0, Integer.MAX_VALUE ) ) )
            {
                mf1.addMark( m.get_first_marked_sample(), m.get_last_marked_sample(), m.getChannel() );
            }
            mf1.writeMarkingsToFile(new FileWriter( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth mark h 0,003 + l 0,005.txt" ));
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
