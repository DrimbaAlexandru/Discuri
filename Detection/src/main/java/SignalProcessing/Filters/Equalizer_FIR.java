package SignalProcessing.Filters;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import Utils.Interval;

/**
 * Created by Alex on 13.02.2018.
 */
public class Equalizer_FIR
{
    private int tap_nr;
    private float[] b;

    public void apply( float[] x, Interval range ) throws DataSourceException
    {
        Interval applying_range = new Interval( range.l, range.r, false );
        if( x.length < range.r || range.l < 0 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( tap_nr % 2 != 1 )
        {
            throw new DataSourceException( "Filter must have an odd number of taps", DataSourceExceptionCause.INVALID_STATE );
        }
        if( tap_nr > 1 )
        {
            int i, j, buf_pos;
            float newVal;
            float buffer[] = new float[ tap_nr / 2 ];
            buf_pos = buffer.length - 1;

            applying_range.l += tap_nr / 2;
            applying_range.r += tap_nr / 2;

            for( i = applying_range.r - 1; i >= applying_range.l - tap_nr / 2; i-- )
            {
                newVal = 0;
                if( i >= applying_range.l )
                {
                    for( j = Math.max( 0, i - x.length + 1 ); j < Math.min( i + 1, tap_nr ); j++ ) // b[j]
                    {
                        newVal += b[ j ] * x[ i - j ];
                    }
                }
                if( i <= applying_range.r - 1 - tap_nr / 2 )
                {
                    x[ i ] = buffer[ buf_pos ];
                }
                if( i >= applying_range.l )
                {
                    buffer[ buf_pos ] = newVal;
                }

                buf_pos--;
                if( buf_pos < 0 )
                {
                    buf_pos = buffer.length - 1;
                }
            }
        }
        else
        {
            int i;
            for( i = applying_range.l; i < applying_range.r; i++ )
            {
                x[ i ] *= b[ 0 ];
            }
        }
    }

    public Equalizer_FIR( FIR fir )
    {
        b = new float[ fir.getFf_coeff_nr() ];
        tap_nr = fir.getFf_coeff_nr();
        System.arraycopy( fir.getFf(), 0, b, 0, tap_nr );
    }

}
