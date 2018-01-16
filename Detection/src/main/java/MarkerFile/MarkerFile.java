package MarkerFile;

import java.io.*;
import java.text.ParseException;
import java.util.*;

/**
 * Created by Alex on 10.11.2017.
 */
public class MarkerFile
{
    private TreeMap< Integer, Marking > l_markings;
    private TreeMap< Integer, Marking > r_markings;
    private String path;

    public MarkerFile( String filePath )
    {
        path = filePath;
        l_markings = new TreeMap<>();
        r_markings = new TreeMap<>();
    }

    public void addMark( int fms, int lms, int ch )
    {
        deleteMark( fms, lms, ch );
        TreeMap< Integer, Marking > map = ( ch == 0 ? l_markings : r_markings );
        Marking tbm = ( map.floorKey( fms ) != null ) ? map.floorEntry( fms ).getValue() : null;
        boolean update_existing = false;

        if( ( tbm != null ) && ( tbm.last_marked_sample >= fms - 1 ) )
        {
            tbm.last_marked_sample = lms;
            update_existing = true;
        }
        else
        {
            tbm = new Marking( fms, lms );
        }

        Marking last = ( map.ceilingKey( lms ) ) != null ? map.ceilingEntry( lms ).getValue() : null;
        if( ( last != null ) && ( last.first_marked_sample == lms + 1 ) )
        {
            tbm.last_marked_sample = last.last_marked_sample;
            map.remove( last.first_marked_sample );
        }

        if( !update_existing )
        {
            map.put( tbm.first_marked_sample, tbm );
        }
    }

    public void deleteMark( int fms, int lms, int ch )
    {
        TreeMap< Integer, Marking > map = ( ch == 0 ? l_markings : r_markings );
        List< Integer > keys_tbd = new ArrayList<>();
        Marking tba = null;
        for( Marking m : map.values() )
        {
            if( ( m.last_marked_sample >= fms ) && ( m.first_marked_sample <= lms ) ) //contains samples to be unmarked
            {
                if( m.last_marked_sample > lms )
                {
                    tba = new Marking( lms + 1, m.last_marked_sample );
                }
                if( m.first_marked_sample < fms )
                {
                    m.last_marked_sample = fms - 1;
                }
                else
                {
                    keys_tbd.add( m.first_marked_sample );
                }
            }
        }
        for( int k : keys_tbd )
        {
            map.remove( k );
        }
        if( tba != null )
        {
            map.put( tba.first_marked_sample, tba );
        }

    }

    public void writeMarkingsToFile( OutputStreamWriter o ) throws IOException
    {
        for( Marking m : l_markings.values() )
        {
            o.write( "ch0 " + m.first_marked_sample + " " + m.last_marked_sample + "\r\n" );
        }
        for( Marking m : r_markings.values() )
        {
            o.write( "ch1 " + m.first_marked_sample + " " + m.last_marked_sample + "\r\n" );
        }
        o.close();
    }

    public void writeMarkingsToFile() throws IOException
    {
        OutputStreamWriter os = new OutputStreamWriter( new FileOutputStream( path ) );
        writeMarkingsToFile( os );
    }

    public static MarkerFile fromFile( String file ) throws FileNotFoundException, ParseException
    {
        FileInputStream fis = new FileInputStream( file );
        Scanner sc = new Scanner( fis );
        MarkerFile mf = new MarkerFile( file );
        String line;
        int fmi, lmi;

        while( sc.hasNextLine() )
        {
            try
            {
                line = sc.findInLine( "ch[01] " );
                fmi = sc.nextInt();
                lmi = sc.nextInt();
                if( line.charAt( 2 ) == '0' )
                {
                    mf.l_markings.put( fmi, new Marking( fmi, lmi ) );
                }
                else
                {
                    mf.r_markings.put( fmi, new Marking( fmi, lmi ) );
                }
                line = sc.nextLine();

            }
            catch( Exception e )
            {
                throw new ParseException( "Parse failed!", 0 );
            }

        }
        return mf;
    }

    public Marking getNextMark( int current_index )
    {
        Marking l_m = null, r_m = null;
        Map.Entry< Integer, Marking > entry;
        entry = l_markings.ceilingEntry( current_index );
        if( entry != null )
        {
            l_m = entry.getValue();
        }
        entry = r_markings.ceilingEntry( current_index );
        if( entry != null )
        {
            r_m = entry.getValue();
        }
        if( l_m == null )
        {
            return r_m;
        }
        else
        {
            if( r_m != null )
            {
                return l_m.first_marked_sample < r_m.first_marked_sample ? l_m : r_m;
            }
            else
            {
                return l_m;
            }
        }
    }
    public boolean isMarked( int sample, int ch )
    {
        Map.Entry< Integer, Marking > entry = ( ch == 0 ) ? l_markings.floorEntry( sample ) : r_markings.floorEntry( sample );
        return ( ( entry != null ) && ( entry.getValue().first_marked_sample <= sample ) && ( entry.getValue().last_marked_sample >= sample ) );
    }

}
