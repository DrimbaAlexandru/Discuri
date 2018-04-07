package MarkerFile;

import Utils.Interval;

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

        if( ( tbm != null ) && ( tbm.get_last_marked_sample() >= fms - 1 ) )
        {
            tbm.set_last_marked_sample( lms );
            update_existing = true;
        }
        else
        {
            tbm = new Marking( fms, lms, ch );
        }

        Marking last = ( map.ceilingKey( lms ) ) != null ? map.ceilingEntry( lms ).getValue() : null;
        if( ( last != null ) && ( last.get_first_marked_sample() == lms + 1 ) )
        {
            tbm.set_last_marked_sample( last.get_last_marked_sample() );
            map.remove( last.get_first_marked_sample() );
        }

        if( !update_existing )
        {
            map.put( tbm.get_first_marked_sample(), tbm );
        }
    }

    public void deleteMark( int fms, int lms, int ch )
    {
        TreeMap< Integer, Marking > map = ( ch == 0 ? l_markings : r_markings );
        List< Integer > keys_tbd = new ArrayList<>();
        Marking tba = null;
        for( Marking m : map.values() )
        {
            if( ( m.get_last_marked_sample() >= fms ) && ( m.get_first_marked_sample() <= lms ) ) //contains samples to be unmarked
            {
                if( m.get_last_marked_sample() > lms )
                {
                    tba = new Marking( lms + 1, m.get_last_marked_sample(),ch );
                }
                if( m.get_first_marked_sample() < fms )
                {
                    m.set_last_marked_sample( fms - 1 );
                }
                else
                {
                    keys_tbd.add( m.get_first_marked_sample() );
                }
            }
        }
        for( int k : keys_tbd )
        {
            map.remove( k );
        }
        if( tba != null )
        {
            map.put( tba.get_first_marked_sample(), tba );
        }

    }

    public void writeMarkingsToFile( OutputStreamWriter o ) throws IOException
    {
        for( Marking m : l_markings.values() )
        {
            o.write( "ch0 " + m.get_first_marked_sample() + " " + m.get_last_marked_sample() + "\r\n" );
        }
        for( Marking m : r_markings.values() )
        {
            o.write( "ch1 " + m.get_first_marked_sample() + " " + m.get_last_marked_sample() + "\r\n" );
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
                    mf.l_markings.put( fmi, new Marking( fmi, lmi, 0 ) );
                }
                else
                {
                    mf.r_markings.put( fmi, new Marking( fmi, lmi, 1 ) );
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

    public Interval getNextMark( int current_index, int channel )
    {
        Marking mark = null;
        Map.Entry< Integer, Marking > entry;
        TreeMap< Integer, Marking > markings;
        switch( channel )
        {
            case 0:
                markings = l_markings;
                break;
            case 1:
                markings = r_markings;
                break;
            default:
                return null;
        }

        entry = markings.ceilingEntry( current_index );
        if( entry != null )
        {
            return new Interval( entry.getValue().get_first_marked_sample(), entry.getValue().get_last_marked_sample() - entry.getValue().get_first_marked_sample() + 1 );
        }
        else
        {
            return null;
        }
    }

    public boolean isMarked( int sample, int ch )
    {
        Map.Entry< Integer, Marking > entry = ( ch == 0 ) ? l_markings.floorEntry( sample ) : r_markings.floorEntry( sample );
        return ( ( entry != null ) && ( entry.getValue().get_first_marked_sample() <= sample ) && ( entry.getValue().get_last_marked_sample() >= sample ) );
    }

    public List< Marking > get_all_markings( Interval interval )
    {
        List< Marking > markings = new ArrayList< Marking >();
        for( TreeMap< Integer, Marking > map : new TreeMap[]{ l_markings, r_markings } )
        {
            Map.Entry< Integer, Marking > entry = map.ceilingEntry( interval.l );
            while( entry != null && entry.getValue().get_last_marked_sample() < interval.r )
            {
                markings.add( new Marking( entry.getValue().get_first_marked_sample(), entry.getValue().get_last_marked_sample(), entry.getValue().getChannel() ) );
                entry = map.ceilingEntry( entry.getValue().get_last_marked_sample() + 1 );
            }
        }
        return markings;
    }

}
