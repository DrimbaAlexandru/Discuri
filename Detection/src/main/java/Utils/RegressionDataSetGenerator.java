package Utils;

        import AudioDataSource.AudioSamplesWindow;
        import AudioDataSource.IAudioDataSource;
        import Utils.DataTypes.Interval;
        import Utils.DataTypes.MyPair;
        import Utils.Exceptions.DataSourceException;

        import java.io.*;
        import java.nio.ByteBuffer;
        import java.nio.ByteOrder;
        import java.util.List;
        import java.util.Random;

public class RegressionDataSetGenerator
{
    private static ByteBuffer buf;
    private int input_size;
    private int output_size;
    private int offset;

    private long total_written = 0;
    private long written = 0;
    private float total_avg = 0.0f;
    private double avg = 0.0;

    public RegressionDataSetGenerator( int input_size, int output_size, int offset )
    {
        this.input_size = input_size;
        this.offset = offset;
        this.output_size = output_size;
    }

    public void generate( IAudioDataSource dataSource, Interval interval, IAudioDataSource damageSource, String destination_path, int near_window_size, List< MyPair<Float,Float> > prob_mapping ) throws DataSourceException, IOException
    {
        final int fetch_size = dataSource.get_sample_rate() + ( input_size - output_size );

        Random rand = new Random();

        interval.limit( 0, dataSource.get_sample_number() );
        buf = ByteBuffer.allocate( input_size * 2 + output_size * 2 );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        written = 0;
        avg = 0.0;

        FileOutputStream fos = new FileOutputStream( destination_path );
        BufferedOutputStream bos = new BufferedOutputStream( fos );
        DataOutputStream writer = new DataOutputStream( bos );

        int i, j, ch;
        boolean willWrite;
        AudioSamplesWindow audioWin;
        AudioSamplesWindow damageWin;
        float prob;
        float damage;

        for( i = interval.l; i < interval.r - input_size; )
        {
            audioWin = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );
            damageWin = damageSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

            for( ch = 0; ch < audioWin.get_channel_number(); ch++ )
            {
                /* j is the index of the first output */
                for( j = offset; j < audioWin.get_length() - ( input_size - output_size ) - offset; j++ )
                {
                    damage = damageWin.getSample( i, ch );
                    prob = get_probability( damage, prob_mapping );

                    willWrite = rand.nextFloat() < prob;

                    while( willWrite )
                    {
                        if( ( written / output_size ) % 50000 == 0 )
                        {
                            System.out.println( "Written " + ( written / output_size ) );
                            System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            System.out.println( "Mean average: " + avg / written );
                        }

                        write_case( audioWin, i + j - offset, ch, damage, writer );

                        willWrite = rand.nextFloat() < prob / 2;
                    }
                }
            }
            i += audioWin.get_length() - ( input_size - output_size );

        }
        writer.close();

        System.out.println( destination_path );
        System.out.println( "Written " + ( written / output_size ) );
        System.out.println( "Mean average: " + avg / written );

        total_written += written;
        total_avg += avg;
    }

    private float get_probability( float damage, List< MyPair< Float, Float > > prob_mapping )
    {
        int i;

        if( damage < prob_mapping.get( 0 ).getLeft() )
        {
            return prob_mapping.get( 0 ).getRight();
        }
        for( i = 0; i < prob_mapping.size() - 1; i++ )
        {
            if( damage >= prob_mapping.get( i ).getLeft() )
            {
                float position = ( damage - prob_mapping.get( i ).getLeft() / ( prob_mapping.get( i + 1 ).getLeft() - prob_mapping.get( i ).getLeft() ) );
                return prob_mapping.get( i ).getRight() + position * ( prob_mapping.get( i + 1 ).getRight() - prob_mapping.get( i ).getRight() );
            }
        }
        return prob_mapping.get( i ).getRight();
    }

    private void write_case( AudioSamplesWindow win, int sample_start_idx, int ch, float damage, DataOutputStream writer ) throws DataSourceException, IOException
    {
        buf.rewind();
        for( int k = sample_start_idx; k < sample_start_idx + input_size; k++ )
        {
            buf.putShort( ( short )( win.getSample( k, ch ) * 32768 ) );
        }
        for( int k = sample_start_idx + offset; k < sample_start_idx + offset + output_size; k++ )
        {
            buf.putShort( ( short )( damage * 32768 ) );

            written++;
            avg += damage;
        }
        writer.write( buf.array(), 0, buf.position() );
    }

    public void write_final_results()
    {
        System.out.println( "Written " + ( total_written ) / output_size );
        System.out.println( "Mean average: " + total_avg / total_written );
    }
}
