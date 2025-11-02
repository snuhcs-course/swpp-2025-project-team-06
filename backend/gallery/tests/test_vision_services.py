from django.test import TestCase

from unittest.mock import patch, MagicMock
from gallery.vision_service import phrase_to_words, get_image_captions


class VisionServicesTest(TestCase):
    def test_phrase_to_words_basic(self):
        """Tests basic lowercase conversion, punctuation removal, and stop word filtering."""
        phrase = "A quick brown fox jumps over the lazy dog."
        expected = set(["brown", "dog", "fox", "jumps", "lazy", "over", "quick"])
        self.assertEqual(set(phrase_to_words(phrase)), expected)

    def test_phrase_to_words_stop_word(self):
        """Tests filtering of custom stop words like 'photo' and 'image'."""
        text = "This is a photo of a beautiful image."
        expected = ["beautiful"]
        self.assertEqual(phrase_to_words(text), expected)

    def test_phrase_to_words_empty(self):
        """Tests handling of an empty input string."""
        text = ""
        expected = []
        self.assertEqual(phrase_to_words(text), expected)

    def test_phrase_to_words_removes_numbers(self):
        """Tests that numbers are removed by the regex."""
        text = "There are 3 cats and 2 dogs in 2024."
        expected = set(["cats", "dogs", "there"])
        self.assertEqual(set(phrase_to_words(text)), expected)

    @patch(
        "gallery.vision_service.torch.no_grad"
    )  # Patch torch.no_grad context manager
    @patch("gallery.vision_service.Image.open")  # Patch Image.open
    @patch("gallery.vision_service.get_caption_model")  # Patch your model loader
    @patch(
        "gallery.vision_service.get_caption_processor"
    )  # Patch your processor loader
    @patch("builtins.print")  # Patch the built-in print to check logs
    def test_successful_caption_generation(
        self,
        mock_print,
        mock_get_processor,
        mock_get_model,
        mock_image_open,
        mock_no_grad,
    ):
        """Tests the end-to-end logic with mocked dependencies."""

        mock_image = MagicMock()
        mock_image_open.return_value = mock_image
        mock_image.convert.return_value = "converted_image_object"

        mock_processor = MagicMock()
        mock_get_processor.return_value = mock_processor

        mock_processor.return_value = {"inputs_tensor": "dummy_tensor"}

        fake_decoded_phrases = [
            "A photo of a brown dog",
            "a brown dog running on the grass",
            "this is a dog",
            "dog playing",
            "image of a happy dog",
        ]
        mock_processor.decode.side_effect = fake_decoded_phrases

        mock_model = MagicMock()
        mock_get_model.return_value = mock_model

        # Simulate the model generating 5 outputs (one for each phrase)
        mock_model.generate.return_value = [
            "raw_output_1",
            "raw_output_2",
            "raw_output_3",
            "raw_output_4",
            "raw_output_5",
        ]

        expected_counts = {
            "dog": 5,
            "brown": 2,
            "grass": 1,
            "happy": 1,
            "playing": 1,
            "running": 1,
        }

        test_path = "fake/image/path.jpg"
        result = get_image_captions(test_path)

        self.assertEqual(result, expected_counts)

        mock_image_open.assert_called_once_with(test_path)
        mock_image.convert.assert_called_once_with("RGB")

        mock_processor.assert_called_once_with(
            images="converted_image_object", return_tensors="pt"
        )

        mock_model.generate.assert_called_once_with(
            inputs_tensor="dummy_tensor",  # This comes from **inputs
            max_new_tokens=20,
            do_sample=True,
            top_k=50,
            top_p=0.95,
            num_return_sequences=5,
        )

    @patch("gallery.vision_service.torch.no_grad")
    @patch("gallery.vision_service.Image.open")
    @patch("gallery.vision_service.get_caption_model")
    @patch("gallery.vision_service.get_caption_processor")
    @patch("builtins.print")
    def test_empty_captions_returned(
        self,
        mock_print,
        mock_get_processor,
        mock_get_model,
        mock_image_open,
        mock_no_grad,
    ):
        """Tests the case where the model returns only stop-words or empty strings."""

        mock_image = MagicMock()
        mock_image_open.return_value = mock_image

        mock_processor = MagicMock()
        mock_get_processor.return_value = mock_processor

        mock_processor.return_value = {"inputs": "dummy"}
        mock_model = MagicMock()

        mock_get_model.return_value = mock_model
        mock_model.generate.return_value = ["out1", "out2"]

        # Return phrases that will be filtered out
        mock_processor.decode.side_effect = [
            "a photo of an image",  # All stop words
            "...",  # All punctuation
        ]

        result = get_image_captions("fake.jpg")

        self.assertEqual(result, {})
