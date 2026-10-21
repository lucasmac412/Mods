"""Safety checks for a source migration that changes hundreds of Java files."""
import unittest
from prepare import rewrite, class_exists


class MigrationSafetyTest(unittest.TestCase):
    def test_java_literals_comments_and_text_blocks_are_preserved(self):
        before = '''// ResourceLocation
/* net.minecraft.Util */
String a = "ResourceLocation";
String b = "escaped \\\" ResourceLocation";
String c = """ResourceLocation
net.minecraft.Util""";
ResourceLocation id = net.minecraft.Util.work();
'''
        after = rewrite(before, {'ResourceLocation': 'Identifier', 'net.minecraft.Util': 'net.minecraft.util.Util'})
        self.assertEqual(before[:before.rfind('ResourceLocation id')], after[:after.rfind('Identifier id')])
        self.assertTrue(after.endswith('Identifier id = net.minecraft.util.Util.work();\n'))

    def test_identifier_rename_does_not_change_other_identifiers(self):
        self.assertEqual('Identifier id; String ResourceLocationLabel;',
                         rewrite('ResourceLocation id; String ResourceLocationLabel;', {'ResourceLocation': 'Identifier'}))

    def test_existing_nested_types_cannot_be_mistaken_for_unrelated_top_level_types(self):
        entries = {'net/minecraft/world/level/ClipContext$Block.class',
                   'net/minecraft/world/scores/criteria/ObjectiveCriteria$RenderType.class',
                   'net/minecraft/client/renderer/rendertype/RenderType.class'}
        self.assertTrue(class_exists('net.minecraft.world.level.ClipContext.Block', entries))
        self.assertTrue(class_exists('net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType', entries))
        self.assertTrue(class_exists('net.minecraft.client.renderer.rendertype.RenderType', entries))
        self.assertFalse(class_exists('net.minecraft.client.renderer.RenderType', entries))


if __name__ == '__main__':
    unittest.main()
