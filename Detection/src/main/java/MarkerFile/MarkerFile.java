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
    private OrderedNonOverlappingIntervalSet ch0_markings = new OrderedNonOverlappingIntervalSet();
    private OrderedNonOverlappingIntervalSet ch1_markings = new OrderedNonOverlappingIntervalSet();
    private String path;

    public MarkerFile( String filePath )
    {
        path = filePath;
    }

    public void addMark( int fms, int lms, int ch )
    {
        switch( ch )
        {
            case 0:
                ch0_markings.add( new Interval( fms, lms + 1, false ) );
                break;
            case 1:
                ch1_markings.add( new Interval( fms, lms + 1, false ) );
                break;
        }
    }

    public void deleteMark( int fms, int lms, int ch )
    {
        switch( ch )
        {
            case 0:
                ch0_markings.remove( new Interval( fms, lms + 1, false ) );
                break;
            case 1:
                ch1_markings.remove( new Interval( fms, lms + 1, false ) );
                break;
        }
    }

    public void writeMarkingsToFile( OutputStreamWriter o ) throws IOException
    {
        Interval i;
        ch0_markings.moveCursorOnFirst();
        ch1_markings.moveCursorOnFirst();

        i = ch0_markings.getCurrent();
        while( i != null )
        {
            o.write( "ch0 " + i.l + " " + ( i.r - 1 ) + "\r\n" );
            ch0_markings.moveCursorNext();
            i = ch0_markings.getCurrent();
        }

        i = ch1_markings.getCurrent();
        while( i != null )
        {
            o.write( "ch1 " + i.l + " " + ( i.r - 1 ) + "\r\n" );
            ch1_markings.moveCursorNext();
            i = ch1_markings.getCurrent();
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
                    mf.addMark( fmi, lmi, 0 );
                }
                else
                {
                    mf.addMark( fmi, lmi, 1 );
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
        OrderedNonOverlappingIntervalSet set;
        switch( channel )
        {
            case 0:
                set = ch0_markings;
                break;
            case 1:
                set = ch1_markings;
                break;
            default:
                return null;
        }

        set.moveCursorOnOrAfter( current_index );
        return set.getCurrent();
    }


    public OrderedNonOverlappingIntervalSet getCopy( int ch )
    {
        switch( ch )
        {
            case 0:
                return ch0_markings.Clone();

            case 1:
                return ch1_markings.Clone();

            default:
                return null;
        }
    }

    public List< Marking > get_all_markings( Interval interval )
    {
        List< Marking > markings = new ArrayList< Marking >();

        OrderedNonOverlappingIntervalSet set1,set2;
        set1 = ch0_markings.Clone();
        set2 = ch1_markings.Clone();
        set1.moveCursorOnOrAfter( interval.l );
        set2.moveCursorOnOrAfter( interval.l );

        Interval ch0 = set1.getCurrent(), ch1 = set2.getCurrent();

        while( ch0 != null || ch1 != null )
        {
            if( ch0 == null || ( ch1 != null && ch0.l > ch1.l ) )
            {
                markings.add( new Marking( ch1.l, ch1.r - 1, 1 ) );
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
                markings.add( new Marking( ch0.l, ch0.r - 1, 0 ) );
                set1.moveCursorNext();
                ch0 = set1.getCurrent();
                if( ch0 != null && ch0.l >= interval.r )
                {
                    ch0 = null;
                }
                continue;
            }
        }

        return markings;
    }

    public boolean isMarked( int sample, int ch )
    {
        switch( ch )
        {
            case 0:
                ch0_markings.moveCursorOnOrAfter( sample );
                return ch0_markings.getCurrent() != null && ch0_markings.getCurrent().contains( sample );

            case 1:
                ch1_markings.moveCursorOnOrAfter( sample );
                return ch1_markings.getCurrent() != null && ch1_markings.getCurrent().contains( sample );

            default:
                return false;
        }
    }
}
