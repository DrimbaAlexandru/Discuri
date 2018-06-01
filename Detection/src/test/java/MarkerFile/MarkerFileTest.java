package MarkerFile;

import Utils.Interval;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by Alex on 01.06.2018.
 */
public class MarkerFileTest
{
    @Test
    public void get_all_markings() throws Exception
    {
        MarkerFile mf = new MarkerFile( "" );
        mf.addMark( 500, 599, 1 );
        mf.addMark( 600, 699, 0 );
        mf.addMark( 700, 799, 1 );
        mf.addMark( 800, 899, 0 );
        mf.addMark( 900, 999, 1 );
        mf.addMark( 0, 99, 0 );
        mf.addMark( 100, 199, 1 );
        mf.addMark( 200, 299, 0 );
        mf.addMark( 300, 399, 1 );
        mf.addMark( 400, 499, 0 );
        mf.addMark( 2000, 2049, 1 );
        mf.addMark( 2000, 2049, 0 );

        List< Marking > markings = mf.get_all_markings( new Interval( 0, 2500, false ) );

        assertEquals( markings.size(), 12 );

        assertEquals( markings.get( 0 ), new Marking( 0, 99, 0 ) );
        assertEquals( markings.get( 1 ), new Marking( 100, 199, 1 ) );

        assertEquals( markings.get( 2 ), new Marking( 200, 299, 0 ) );
        assertEquals( markings.get( 3 ), new Marking( 300, 399, 1 ) );

        assertEquals( markings.get( 4 ), new Marking( 400, 499, 0 ) );
        assertEquals( markings.get( 5 ), new Marking( 500, 599, 1 ) );

        assertEquals( markings.get( 6 ), new Marking( 600, 699, 0 ) );
        assertEquals( markings.get( 7 ), new Marking( 700, 799, 1 ) );

        assertEquals( markings.get( 8 ), new Marking( 800, 899, 0 ) );
        assertEquals( markings.get( 9 ), new Marking( 900, 999, 1 ) );

        assertEquals( markings.get( 10 ), new Marking( 2000, 2049, 0 ) );
        assertEquals( markings.get( 11 ), new Marking( 2000, 2049, 1 ) );
    }

}