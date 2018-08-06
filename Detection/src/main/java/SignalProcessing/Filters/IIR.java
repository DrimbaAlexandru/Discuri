package SignalProcessing.Filters;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;
import Utils.Interval;

/**
 * Created by Alex on 22.01.2018.
 */
public class IIR
{
    private int ff_coeff_nr;    // P+1 in formula
    private float[] ff;        // b in formula
    private int fb_coeff_nr;    // Q+1 in formula
    private float[] fb;        // a in formula

    public final static IIR integration_IIR = new IIR( FIR.identity_FIR.getFf(), FIR.identity_FIR.getFf_coeff_nr(), new float[]{ 1, -1 }, 2 );

    public void apply( float[] x, Interval range, boolean use_input_as_output_start ) throws DataSourceException
    {
        FIR fir = new FIR( ff, ff_coeff_nr );
        int i, j;
        float newVal;

        if( x.length < range.r )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        fir.apply( x, range );

        if( !use_input_as_output_start )
        {
            for( i = 0; i < range.l; i++ )
            {
                x[ i ] = 0;
            }
        }

        for( i = range.l; i < range.r; i++ )
        {
            newVal = x[ i ];
            for( j = 1; j < Math.min( i + 1, fb_coeff_nr ); j++ )
            {
                newVal -= fb[ j ] * x[ i - j ];
            }
            newVal *= fb[ 0 ];
            x[ i ] = newVal;
        }

    }

    public IIR( float ff_coeffs[], int ff_tap_nr, float fb_coeffs[], int fb_tap_nr )
    {
        ff = new float[ ff_tap_nr ];
        ff_coeff_nr = ff_tap_nr;
        System.arraycopy( ff_coeffs, 0, ff, 0, ff_tap_nr );

        fb = new float[ fb_tap_nr ];
        fb_coeff_nr = fb_tap_nr;
        System.arraycopy( fb_coeffs, 0, fb, 0, fb_tap_nr );
    }

    public int getFf_coeff_nr()
    {
        return ff_coeff_nr;
    }

    public int getFb_coeff_nr()
    {
        return fb_coeff_nr;
    }

    public float[] getFb()
    {
        return fb;
    }

    public float[] getFf()
    {
        return ff;
    }
}
