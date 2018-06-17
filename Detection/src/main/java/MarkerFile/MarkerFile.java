package MarkerFile;

import Utils.DataStructures.OrderedNonOverlappingIntervalSet;
import Utils.Interval;

import java.io.*;
import java.text.ParseException;
import java.util.*;

/**
 * Created by Alex on 10.11.2017.
 */
public class MarkerFile
{
    private HashMap< Integer, OrderedNonOverlappingIntervalSet > markings = new HashMap<>();

    public MarkerFile()
    {
        markings.put( 0, new OrderedNonOverlappingIntervalSet() );
        markings.put( 1, new OrderedNonOverlappingIntervalSet() );
    }

    public void addMark( int fms, int lms, int ch )
    {
        OrderedNonOverlappingIntervalSet set = markings.get( ch );
        if( set == null )
        {
            set = markings.put( ch, new OrderedNonOverlappingIntervalSet() );
        }
        set.add( new Interval( fms, lms + 1, false ) );
    }

    public void deleteMark( int fms, int lms, int ch )
    {
        OrderedNonOverlappingIntervalSet set = markings.get( ch );
        if( set == null )
        {
            return;
        }
        set.remove( new Interval( fms, lms + 1, false ) );
    }

    public void writeMarkingsToFile( OutputStreamWriter o ) throws IOException
    {
        Interval i;
        OrderedNonOverlappingIntervalSet set;
        for( int ch : markings.keySet() )
        {
            set = markings.get( ch );
            set.moveCursorOnFirst();
            i = set.getCurrent();
            while( i != null )
            {
                o.write( "ch" + ch + " " + i.l + " " + ( i.r - 1 ) + "\r\n" );
                set.moveCursorNext();
                i = set.getCurrent();
            }
        }
        o.close();
    }

    public void add_from_file( String file ) throws FileNotFoundException, ParseException
    {
        FileInputStream fis = new FileInputStream( file );
        Scanner sc = new Scanner( fis );
        String line;
        int fmi, lmi;
        int ch;

        while( sc.hasNextLine() )
        {
            try
            {
                line = sc.findInLine( "ch[\\d+] " );
                fmi = sc.nextInt();
                lmi = sc.nextInt();
                ch = Integer.parseInt( line.substring( 2, line.length() - 1 ) );
                addMark( fmi, lmi, ch );
                /*if( line.charAt( 2 ) == '0' )
                {
                    addMark( fmi, lmi, 0 );
                }
                else
                {
                    addMark( fmi, lmi, 1 );
                }*/
                sc.nextLine();
            }
            catch( Exception e )
            {
                throw new ParseException( "Parse error!", 0 );
            }
        }
    }

    public static MarkerFile fromFile( String file ) throws FileNotFoundException, ParseException
    {
        MarkerFile mf = new MarkerFile();
        mf.add_from_file( file );
        return mf;
    }


    public Interval getNextMark( int current_index, int channel )
    {
        OrderedNonOverlappingIntervalSet set = markings.get( channel );
        if( set == null )
        {
            return null;
        }
        set.moveCursorOnOrAfter( current_index );
        return set.getCurrent();
    }


    public List< Marking > get_all_markings( Interval interval )
    {
        List< Marking > markings_list = new ArrayList< Marking >();

        OrderedNonOverlappingIntervalSet set1,set2;
        set1 = markings.get( 0 ).Clone();
        set2 = markings.get( 1 ).Clone();
        set1.moveCursorOnOrAfter( interval.l );
        set2.moveCursorOnOrAfter( interval.l );

        Interval ch0 = set1.getCurrent(), ch1 = set2.getCurrent();

        while( ch0 != null || ch1 != null )
        {
            if( ch0 == null || ( ch1 != null && ch0.l > ch1.l ) )
            {
                markings_list.add( new Marking( ch1.l, ch1.r - 1, 1 ) );
                set2.moveCursorNext();
                ch1 = set2.getCurrent();
                if( ch1 != null && ch1.l >= interval.r )
                {
                    ch1 = null;
                }
                continue;
            }

            if( ch1 == null || ( ch0 != null && ch0.l <= ch1.l ) )
            {
                markings_list.add( new Marking( ch0.l, ch0.r - 1, 0 ) );
                set1.moveCursorNext();
                ch0 = set1.getCurrent();
                if( ch0 != null && ch0.l >= interval.r )
                {
                    ch0 = null;
                }
                continue;
            }
        }

        return markings_list;
    }

    public boolean isMarked( int sample, int ch )
    {
        OrderedNonOverlappingIntervalSet set = markings.get( ch );
        if( set == null )
        {
            return false;
        }
        return set.getCurrent() != null && set.getCurrent().contains( sample );
    }

    public void clear_all_markings()
    {
        markings.clear();
        markings.put( 0, new OrderedNonOverlappingIntervalSet() );
        markings.put( 1, new OrderedNonOverlappingIntervalSet() );
    }
}
