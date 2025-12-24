package viewers

import spock.lang.Specification

/**
 * Test specification for SignViewer static constants and data structures.
 * Tests that don't require JavaFX thread or viewer instantiation.
 */
class SignViewerSpec extends Specification {

    def "should have all 12 wood colors defined"() {
        expect:
        SignViewer.WOOD_COLORS.size() == 12
        SignViewer.WOOD_COLORS.containsKey('oak')
        SignViewer.WOOD_COLORS.containsKey('spruce')
        SignViewer.WOOD_COLORS.containsKey('birch')
        SignViewer.WOOD_COLORS.containsKey('jungle')
        SignViewer.WOOD_COLORS.containsKey('acacia')
        SignViewer.WOOD_COLORS.containsKey('dark_oak')
        SignViewer.WOOD_COLORS.containsKey('mangrove')
        SignViewer.WOOD_COLORS.containsKey('cherry')
        SignViewer.WOOD_COLORS.containsKey('crimson')
        SignViewer.WOOD_COLORS.containsKey('warped')
        SignViewer.WOOD_COLORS.containsKey('bamboo')
        SignViewer.WOOD_COLORS.containsKey('pale_oak')
    }

    def "should have correct wood colors with hex values"() {
        expect:
        SignViewer.WOOD_COLORS['oak'] == '#BA8755'
        SignViewer.WOOD_COLORS['spruce'] == '#5A3D2D'
        SignViewer.WOOD_COLORS['birch'] == '#D6CB8E'
        SignViewer.WOOD_COLORS['jungle'] == '#AB7743'
        SignViewer.WOOD_COLORS['acacia'] == '#9F5429'
        SignViewer.WOOD_COLORS['dark_oak'] == '#3E2912'
        SignViewer.WOOD_COLORS['mangrove'] == '#6B3028'
        SignViewer.WOOD_COLORS['cherry'] == '#E4A2A6'
        SignViewer.WOOD_COLORS['crimson'] == '#582A32'
        SignViewer.WOOD_COLORS['warped'] == '#1A7475'
        SignViewer.WOOD_COLORS['bamboo'] == '#D4CE67'
        SignViewer.WOOD_COLORS['pale_oak'] == '#E8E0D0'
    }

    def "should have dimension colors defined"() {
        expect:
        SignViewer.DIMENSION_COLORS.size() == 4
        SignViewer.DIMENSION_COLORS.containsKey('overworld')
        SignViewer.DIMENSION_COLORS.containsKey('nether')
        SignViewer.DIMENSION_COLORS.containsKey('end')
        SignViewer.DIMENSION_COLORS.containsKey('unknown')
    }

    def "SignViewer class should be loadable"() {
        expect:
        SignViewer != null
        SignViewer.name == 'viewers.SignViewer'
    }
}
