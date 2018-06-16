package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.MemoryADS.InMemoryADS;
import AudioDataSource.MemoryADS.SingleBlockADS;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.Marking;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.Interval;
import Utils.MyPair;
import Utils.Util_Stuff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Alex on 15.06.2018.
 */
public class Multi_Band_Repair_Marked implements IEffect
{
    final private int band_pass_filter_length;
    private ArrayList< Integer > band_cutoffs = new ArrayList<>();
    final private int max_repair_size;
    final private int fetch_ratio;
    final private int buffer_size;
    private boolean repair_residue = false;

    public Multi_Band_Repair_Marked()
    {
        this( 511, 512, 16 );
    }

    public Multi_Band_Repair_Marked( int band_pass_filter_length, int max_repaired_size, int repair_fetch_ratio )
    {
        this.band_pass_filter_length = band_pass_filter_length;
        max_repair_size = max_repaired_size;
        fetch_ratio = repair_fetch_ratio;
        buffer_size = max_repair_size * ( fetch_ratio * 2 + 1 ) + band_pass_filter_length;
    }

    @Override
    public String getName()
    {
        return "Repair marked in multiband";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( dataSource == dataDest )
        {
            throw new DataSourceException( "Data Source and Data Dest cannot be the same", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        band_cutoffs.sort( Comparator.reverseOrder() );
        /*
            Local variables.
            Filter definitions
        */
        final int nr_of_bands = band_cutoffs.size();
        final FIR[] filters = new FIR[ nr_of_bands ];
        final int nr_freqs = dataSource.get_sample_rate() / 2 / 50;
        final double[] freqs = new double[ nr_freqs ];
        final double[] hp_response = new double[ nr_freqs ];

        for( int i = 0; i < nr_freqs; i++ )
        {
            freqs[ i ] = 1.0 * i * dataSource.get_sample_rate() / 2 / nr_freqs;
        }
        for( int f = 0; f < nr_of_bands; f++ )
        {
            Arrays.setAll( hp_response, x -> 0 );
            FIR.add_pass_cut_freq_resp( freqs, hp_response, nr_freqs, band_cutoffs.get( f ), -96, 0 );
            filters[ f ] = FIR.fromFreqResponse( freqs, hp_response, nr_freqs, dataSource.get_sample_rate(), band_pass_filter_length );
            if( f > 0 )
            {
                double[] coeffs = filters[ f ].getFf();
                double[] prev_coeffs = filters[ f - 1 ].getFf();
                for( int j = 0; j < band_pass_filter_length; j++ )
                {
                    coeffs[ j ] -= prev_coeffs[ j ];
                }
            }
        }

        /*
            Data sources
        */
        final SingleBlockADS repair_dest = new SingleBlockADS( dataSource.get_sample_rate(), dataSource.get_channel_number(), 0, new Interval( 0, 0 ) );
        //workDS[nr_of_bands] - buffer-ul pentru reziduu
        final InMemoryADS workDS[] = new InMemoryADS[ nr_of_bands + 1 ];
        for( int i = 0; i < nr_of_bands + 1; i++ )
        {
            workDS[ i ] = new InMemoryADS( buffer_size, dataSource.get_channel_number(), dataSource.get_sample_rate() );
        }

        /*
        * Effects
        */
        final Equalizer equalizer = new Equalizer();
        final Repair repair = new Repair();
        repair.set_fetch_ratio( fetch_ratio );
        /*
            Indexes and other shit
        */
        int i;
        int second;
        //side length e numarul de sample-uri necesar la dreapta si la stanga selectiei pentru repair, necesare la band-pass
        final int side_length = band_pass_filter_length / 2;

        /*
            Work
        */
        interval.limit( 0, dataSource.get_sample_number() );

        List< Marking > repair_intervals = ProjectStatics.getMarkerFile().get_all_markings( interval );

        repair_intervals.sort( ( i1, i2 ) ->
                               {
                                   return -( i2.getInterval().l - i2.getInterval().get_length() * fetch_ratio ) + ( i1.getInterval().l - i1.getInterval().get_length() * fetch_ratio );
                               } );

        second = -1;

        for( Marking marking : repair_intervals )
        {
            Interval repair_interval = marking.getInterval();
            try
            {
                Interval interval_required_for_prediction = new Interval( repair_interval.l - fetch_ratio * repair_interval.get_length(), repair_interval.r + fetch_ratio * repair_interval.get_length(), false );
                repair.setAffected_channels( Arrays.asList( marking.getChannel() ) );

                if( interval_required_for_prediction.l < 0 || interval_required_for_prediction.r > dataSource.get_sample_number() )
                {
                    throw new DataSourceException( "Not enough samples for linear prediction", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
                }
                if( repair_interval.get_length() > max_repair_size )
                {
                    throw new DataSourceException( "Repair interval too big", DataSourceExceptionCause.WARNING );
                }

                //Make sure all the required samples are in workADSs
                if( !workDS[ 0 ].get_buffer_interval().includes( interval_required_for_prediction ) )
                {
                    //shift left as much as possible and as rare as possible
                    //if( workDS[ 0 ].get_buffer_interval().r < interval_required_for_prediction.r || workDS[ 0 ].get_buffer_interval().l + workDS[ 0 ].getCapacity() < interval_required_for_prediction.r )
                    {
                        int amount_to_shift = interval_required_for_prediction.l - workDS[ 0 ].get_buffer_interval().l;
                        for( i = 0; i <= nr_of_bands; i++ )
                        {
                            workDS[ i ].shift_interval( amount_to_shift );
                        }
                    }
                    Interval not_existing = new Interval( workDS[ 0 ].get_buffer_interval().r, interval_required_for_prediction.r, false );
                    not_existing.limit( interval_required_for_prediction.l, interval_required_for_prediction.r );
                    for( i = 0; i < nr_of_bands; i++ )
                    {
                        equalizer.setFilter( filters[ i ] );
                        equalizer.apply( dataSource, workDS[ i ], not_existing );
                    }
                    AudioSamplesWindow win = dataSource.get_samples( not_existing.l, not_existing.get_length() );
                    for( i = 0; i < nr_of_bands; i++ )
                    {
                        AudioSamplesWindow winb = workDS[ i ].get_samples( not_existing.l, not_existing.get_length() );
                        for( int k = 0; k < win.get_channel_number(); k++ )
                        {
                            for( int j = not_existing.l; j < not_existing.r; j++ )
                            {
                                win.putSample( j, k, win.getSample( j, k ) - winb.getSample( j, k ) );
                            }
                        }
                    }
                    workDS[ nr_of_bands ].put_samples( win );
                    /*for( i = 0; i <= nr_of_bands; i++ )
                    {
                        win = workDS[ i ].get_samples( workDS[ i ].get_buffer_interval().l, workDS[ i ].get_buffer_interval().get_length() );
                        Util_Stuff.plot_in_matlab( win.getSamples()[ 0 ], win.get_length() );
                        System.out.println( "----" );
                    }*/
                }

                //Begin the repair process
                AudioSamplesWindow repaired_base, repaired;
                if( repair_residue )
                {
                    repair.apply( workDS[ nr_of_bands ], repair_dest, repair_interval );
                    repaired_base = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                }
                else
                {
                    repaired_base = workDS[ nr_of_bands ].get_samples( repair_interval.l, repair_interval.get_length() );
                }
                //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                //System.out.println( "----" );
                for( i = 0; i < nr_of_bands; i++ )
                {
                    repair.apply( workDS[ i ], repair_dest, repair_interval );
                    repaired = repair_dest.get_samples( repair_interval.l, repair_interval.get_length() );
                    for( int j = repair_interval.l; j < repair_interval.r; j++ )
                    {
                        repaired_base.getSamples()[ marking.getChannel() ][ j - repair_interval.l ] += repaired.getSample( j, marking.getChannel() );
                    }
                    //Util_Stuff.plot_in_matlab( repaired_samples, repair_interval.get_length() );
                    //System.out.println( "----" );
                }
                repaired = dataDest.get_samples( repair_interval.l, repair_interval.get_length() );
                repaired.getSamples()[ marking.getChannel() ] = repaired_base.getSamples()[ marking.getChannel() ];
                dataDest.put_samples( repaired );
            }
            catch( DataSourceException ex )
            {
                switch( ex.getDSEcause() )
                {
                    case INTERPOLATION_EXCEPTION:
                    case WARNING:
                        ex.printStackTrace();
                        break;
                    default:
                        throw ex;
                }
            }
            if( repair_interval.l / dataSource.get_sample_rate() > second )
            {
                second = repair_interval.l / dataSource.get_sample_rate();
                System.out.println( "Repairing at second " + second );
            }
        }
    }

    public ArrayList< Integer > getBand_cutoffs()
    {
        return band_cutoffs;
    }

    public void setRepair_residue( boolean repair_residue )
    {
        this.repair_residue = repair_residue;
    }
}