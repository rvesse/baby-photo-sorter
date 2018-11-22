# Baby Photo Sorter

If you've become a parent in the past few years you've likely amassed a huge collection of digital photos and videos of your kids.  Depending on how organised you are these may just all be dumped in one big folder and difficult to find specific things.  `baby-photo-sorter` helps automatically organise your baby photos, calculating your child's age for each photo, grouping, organising and renaming photos into appropriate sub-folders.

To use this tool you will first need to build it:

```
> mvn package
```

This will require a recent Apache Maven 3 release and JDK 8 or above (note this hasn't been explicitly tested with later JDK versions)

Then you can organise your photos, for example:

```
> ./baby-photo-sorter --name "John Smith" \
                      --dob 14/4/2017 \
                      --subfolders \
                      --source /my/photos/john/
```

## Dry Run

**IMPORTANT** - If you want to see what the tool will do prior to actually running it on your precious photos then you should use the `--dry-run` option.  This will enable verbose log output (also separately available via the `--verbose` option) and won't actually perform any mutative file system options i.e. it calculates and reports what the tool would do without actually doing it.

# Organisation Criteria

## Photo Discovery

Photos are discovered by scanning each provided source directory (via the `-s`/`--source` option) for files that have an appropriate extension.  By default we only look for `.jpg` and `.jpeg` files.  If you want to change the list of extensions looked for you can use the `-e`/`--extensions` option e.g. `--extensions .jpg,.jpeg,.png,.tiff,.raw`

### Ignoring Directories

If you have specific directories or sub-directories you don't wish to have organised you can specify these via the `--ignore <path>` option.

### Reorganisation

If you run this tool multiple times or want to change your organisation criteria then you should use the `--reorg` option.  When this is used it will scan the source directory, any sub-directories of the source sub-directory and the target directory and any sub-directories thereof.  Previously sorted photos will then be reorganised based on your new organisation criteria.

## Target Directory

The target directory may optionally be supplied via the `-t`/`--target` option.  When specified all photos will be copied/moved into that directory, or sub-directories thereof.

If not specified then each individual source directories contents will be organised under itself.

## Copying vs Moving

The default behaviour of the tool is to rename and move photos to their target directories.  If you prefer to not move your photos then you can use the `--preserve` option in which case photos are copied not moved.

**NB** - You cannot use the `--preserve` option in combination with the `--reorg` option.

## Image Grouping

The tool primarily works by grouping your photos into age/event based groups.  By default only age based groups are used.  Age based groups are calculated based upon the provided date of birth (the `-d`/`--dob`/`--date-of-birth` option) relative to the creation date of the photo.  For photos created prior to the date of birth we attempt to calculate the week of pregnancy, if the optional due date is provided (via the `--due-date` option) we can potentially calculate this more accurately.

Photo creation date is discovered in one of two ways:

- Image Metadata from the image format if available e.g. EXIF in JPEG files, Tiff metadata for TIFF files
- File system creation date

All age based calculations are based upon standard days, weeks, months and years as calculated by the underlying date time library ([Joda Time](https://www.joda.org/joda-time/))  so may differ slightly from your own calculations.

Age based groups start as grouping by days old, then weeks old, months old and finally years old.  The thresholds for when to switch between the different levels of grouping are all configurable.  By default the following applies:

- 0 to 7 days - group by days
- 1 to 13 weeks - group by weeks (configured by `-w`/`--weeks` option)
- 3 to 12 months - group by months (configured by `-m`/`--months` option)
- 1 year + - group by years (configured by `-y`/`--years` option)

### Events

Additionally you can also group by events by providing an events file to the `--events` option, this allows you to define specific events that are used in preference to age based groups.  This file is a simple comma separated file where each line has the format `START,END,NAME` where both `START` and `END` are ISO 8601 format dates i.e. `dd/MM/yyyy HH:mm:ssZ`, if the time portion is omitted then `START` assumes `00:00:00Z` and `END` assumes `23:59:59Z` e.g.

```
14/04/2017 08:30:00+01:00,14/04/2017 15:00:00+01:00,Birth
```

Note that if events overlap then photos are grouped into the first event chronologically. If you have an event that is entirely contained within another event then photos are always grouped to the container event.  If your events meet either of these criteria then the tool will issue warnings to make you aware of this.

### Sequence Numbering

Once photos are grouped they are sorted by creation date and then assigned a sequence number within the group.  Sequence numbering will take into account any existing photos in the target directory.

#### Padding

By default sequence numbers are padded to three digits with leading zeros i.e. a sequence number of `1` would be presented as `001` in file names.  You can adjust the padding of sequence numbers via the `-p`/`--padding` option.

### Subfolders

By default the tool will organise each group (whether age/event) into its own subfolder in the target directory (if using) or under the source directory.  If you prefer to have a single folder then use the `--no-subfolders` option.

### Naming Scheme

Photos will all be renamed according to the selected naming scheme, if no scheme is explicitly specified then we default to `NameGroupSequence`.  The naming scheme can be specified in one of two ways, either via the `--naming-scheme` option which takes one of the pre-defined schemes or via the `--naming-pattern` option which takes a custom pattern.

Pre-defined naming schemes are as follows:

- `NameAgeSequence` - Baby name, age and sequence number
- `NameGroupSequence` - Baby name, group and sequence number
- `AgeNameSequence` - Baby age, name and sequence number
- `GroupNameSequence` - Group, baby name and sequence number
- `NameGroupDate` - Baby name, group and date
- `NameAgeDate` - Baby name, age and date

All elements of pre-defined schemes are separated by whitespace.

Custom patterns are given as a string to the `--naming-pattern` which contains one/more format specifiers:

- `%a` - Baby Age
- `%d` - Date
- `%g` - Group name
- `%n` - Baby name
- `%s` - Sequence number

For example `%s - %g %n` would use Sequence number, group name and baby name with the sequence number separated from the rest by ` - ` i.e. spaces and a hyphen.

## De-duplication

Optionally you can apply de-duplication to your photos via the `--de-duplicate` option.  When enabled the tool will calculate file hashes for each group of photos and report and delete any duplicates.  If you prefer to just report duplicates then use the `--keep-duplicates` option.

Even with this option enabled no photos will be deleted by default without user consent, on the first duplicate detected you will be prompted whether you want to proceed with deletions.  If you don't permit this then the tool will abort.

If you want to allow deletes by default you have to explicitly provide the `--allow-deletes` option.

# License

This software is licensed under the Apache Software License 2.0

# Disclaimer

Any tool that mutates the state of the file system should be used with caution.  We strongly advise taking advantage of the options that preview this tools effects e.g. `--dry-run` prior to running it for real.

You can also use other options like `--preserve` to only copy files, used in conjunction with the `-t`/`--target` option you can create a copy of your photos with your organisation criteria applied and examine the results to ensure you are happy with the output of this tool.