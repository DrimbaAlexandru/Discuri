package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.AUFileAudioSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.Utils;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.util.function.Function;

/**
 * Created by Alex on 08.02.2018.
 */
public class Repair_in_high_pass implements IEffect
{
    private int cutoff_frq = 2000;
    private double preamp = -20.0 / 6;
    private int chunk_size = 1024 * 128;
    private int riaa_length = 2047;
    private int low_pass_length = 511;

    @Override
    public String getName()
    {
        return "Repair in high band of position domain";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        final double preamp_coeffs[] = new double[]{ Math.pow( 2, preamp ) };
        final double postamp_coeffs[] = new double[]{ Math.pow( 2, -preamp ) };
        final double riaa_response[] = FIR.get_RIAA_response( riaa_length, dataSource.get_sample_rate() );
        final double inverse_riaa_response[] = FIR.get_inverse_RIAA_response( riaa_length, dataSource.get_sample_rate() );
        final double low_pass_resp[] = FIR.pass_cut_freq_resp( low_pass_length, cutoff_frq, dataSource.get_sample_rate(), 0, -96 );
        Function< Double, Double > window = Windowing.Blackman_window;

        FIR preamp_filter = new FIR( preamp_coeffs, preamp_coeffs.length );
        FIR postamp_filter = new FIR( postamp_coeffs, postamp_coeffs.length );
        FIR derivation_filter = new FIR( new double[]{ 1, -1 }, 2 );
        IIR integration_filter = new IIR( new double[]{ 1 }, 1, new double[]{ 1, -1 }, 2 );
        FIR riaa_filter = FIR.fromFreqResponse( riaa_response, riaa_length, dataSource.get_sample_rate(), riaa_length );
        FIR inverse_riaa_filter = FIR.fromFreqResponse( inverse_riaa_response, riaa_length, dataSource.get_sample_rate(), riaa_length );
        FIR low_pass_filter = FIR.fromFreqResponse( low_pass_resp, low_pass_length, dataSource.get_sample_rate(), low_pass_length );

        Windowing.apply( inverse_riaa_filter.getB(), inverse_riaa_filter.getTap_nr(), ( v ) -> 1.0 / inverse_riaa_filter.getTap_nr() );
        Windowing.apply( riaa_filter.getB(), riaa_filter.getTap_nr(), ( v ) -> 1.0 / riaa_filter.getTap_nr() );
        Windowing.apply( low_pass_filter.getB(), low_pass_filter.getTap_nr(), ( v ) -> 1.0 / low_pass_filter.getTap_nr() );

        Windowing.apply( inverse_riaa_filter.getB(), inverse_riaa_filter.getTap_nr(), window );
        Windowing.apply( riaa_filter.getB(), riaa_filter.getTap_nr(), window );
        Windowing.apply( low_pass_filter.getB(), low_pass_filter.getTap_nr(), window );

        AUFileAudioSource temp_file1, temp_file2;
        CachedAudioDataSource temp_cache1, temp_cache2;

        Equalizer equalizer = new Equalizer();
        FIR_Filter fir_filter = new FIR_Filter();
        IIR_Filter iir_filter = new IIR_Filter();

        if( interval.l < 0 )
        {
            interval.l = 0;
        }
        if( interval.r > dataSource.get_sample_number() )
        {
            interval.r = dataSource.get_sample_number();
        }

        fir_filter.setMax_chunk_size( chunk_size );
        equalizer.setMax_chunk_size( chunk_size );
        iir_filter.setMax_chunk_size( chunk_size );

        //Amplificare -20dB
        fir_filter.setFilter( preamp_filter );
        fir_filter.apply( dataSource, dataDest, interval );
        System.out.println( "Finished phase 1" );

        //Aplicare RIIA invers
        equalizer.setFilter( inverse_riaa_filter );
        equalizer.apply( dataDest, dataDest, interval );
        System.out.println( "Finished phase 2" );

        //Integrare discreta
        iir_filter.setFilter( integration_filter );
        iir_filter.apply( dataDest, dataDest, interval );
        System.out.println( "Finished phase 3" );

        //Separare low-pass
        temp_file1 = new AUFileAudioSource( ProjectStatics.getTemp_folder() + "low_pass.au", dataDest.get_channel_number(), dataDest.get_sample_rate(), 2 );
        temp_cache1 = new CachedAudioDataSource( temp_file1, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );

        temp_file2 = new AUFileAudioSource( ProjectStatics.getTemp_folder() + "high_pass_repaired.au", dataDest.get_channel_number(), dataDest.get_sample_rate(), 2 );
        temp_cache2 = new CachedAudioDataSource( temp_file2, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );

        //AudioDataSource.Utils.copyToADS( dataDest, temp_cache1 );
        equalizer.setFilter( low_pass_filter );
        equalizer.apply( dataDest, temp_cache1, interval );

        temp_cache1.flushAll();
        System.out.println( "Finished phase 4a" );

        int i;
        int temp_len, k, j;
        AudioSamplesWindow win1, win2;

        //Separare high-pass
        i = interval.l;
        while( i < interval.r )
        {
            temp_len = Math.min( chunk_size, interval.r - i );
            win1 = dataDest.get_samples( i, temp_len );
            win2 = temp_cache1.get_samples( i, temp_len );
            temp_len = win1.get_length();
            if( temp_len == 0 || win1.get_length() != win2.get_length() )
            {
                throw new DataSourceException( "Got wrong length window", DataSourceExceptionCause.GENERIC_ERROR );
            }
            for( k = 0; k < dataDest.get_channel_number(); k++ )
            {
                for( j = win1.get_first_sample_index(); j < win1.get_last_sample_index(); j++ )
                {
                    win1.putSample( j, k, win1.getSample( j, k ) - win2.getSample( j, k ) );
                }
            }
            dataDest.put_samples( win1 );
            i += temp_len;
        }
        System.out.println( "Finished phase 4b" );

        //Reparare in high-pass
        Utils.copyToADS( dataDest, temp_cache2 );
        Repair_Marked repair_marked = new Repair_Marked();
        repair_marked.apply( dataDest, temp_cache2, interval );

        System.out.println( "Finished phase 5" );
        //Combinare high-pass / low-pass
        i = interval.l;
        while( i < interval.r )
        {
            temp_len = Math.min( chunk_size, interval.r - i );
            win1 = temp_cache1.get_samples( i, temp_len );
            win2 = temp_cache2.get_samples( i, temp_len );
            temp_len = win1.get_length();
            if( temp_len == 0 || win1.get_length() != win2.get_length() )
            {
                throw new DataSourceException( "Got wrong length window", DataSourceExceptionCause.GENERIC_ERROR );
            }
            for( k = 0; k < dataDest.get_channel_number(); k++ )
            {
                for( j = win1.get_first_sample_index(); j < win1.get_last_sample_index(); j++ )
                {
                    win1.putSample( j, k, win1.getSample( j, k ) + win2.getSample( j, k ) );
                }
            }
            dataDest.put_samples( win1 );
            i += temp_len;
        }
        System.out.println( "Finished phase 6" );

        //Derivare discreta
        fir_filter.setFilter( derivation_filter );
        fir_filter.apply( dataDest, dataDest, interval );
        System.out.println( "Finished phase 7" );

        //Aplicare RIAA
        equalizer.setFilter( riaa_filter );
        equalizer.apply( dataDest, dataDest, interval );
        System.out.println( "Finished phase 8" );

        //Amplificare +20dB
        fir_filter.setFilter( postamp_filter );
        fir_filter.apply( dataDest, dataDest, interval );
        System.out.println( "Finished phase 9" );

    }
}
