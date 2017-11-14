package MarkerFile;

/**
 * Created by Alex on 10.11.2017.
 */
 class Marking
{
    public int first_sample_index;
    public int length;
    public int channel;

    public Marking( int fsi, int len, int ch )
    {
        first_sample_index = fsi;
        length = len;
        channel = ch;
    }
}
